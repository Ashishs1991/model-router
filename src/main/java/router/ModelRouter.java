package router;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;

import static router.RouteFailure.Code.*;

public final class ModelRouter {
    private final TaskProfiles profiles;
    private final ModelRegistry registry;
    private final RoutingPolicy policy;
    private final BiPredicate<String, String> schemaValidator;
    private final boolean fallback;

    public ModelRouter(TaskProfiles profiles, ModelRegistry registry, RoutingPolicy policy,
                       BiPredicate<String, String> schemaValidator, boolean fallback) {
        this.profiles = Objects.requireNonNull(profiles);
        this.registry = Objects.requireNonNull(registry);
        this.policy = Objects.requireNonNull(policy);
        this.schemaValidator = Objects.requireNonNull(schemaValidator);
        this.fallback = fallback;
    }

    public RouteResult invoke(RouteRequest request) {
        long start = System.nanoTime();
        profiles.validate(request);
        long allowance = TimeUnit.MILLISECONDS.toNanos(request.timeoutMs());
        BigDecimal budget = request.maxCostUsd();
        BigDecimal reserved = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        var attempts = new ArrayList<RouteResult.Attempt>();
        var lastFailure = NO_ELIGIBLE_MODEL;
        for (int i = 0; i < (fallback ? 2 : 1); i++) {
            long remaining = remaining(start, allowance);
            if (remaining <= 0) throw new RouteFailure(DEADLINE_EXCEEDED, attempts);
            var candidate = policy.candidates(registry, request, budget,
                            TimeUnit.NANOSECONDS.toMillis(remaining)).stream()
                    .filter(c -> attempts.stream().noneMatch(a -> a.modelAlias().equals(c.model().alias())))
                    .findFirst();
            if (remaining(start, allowance) <= 0) throw new RouteFailure(DEADLINE_EXCEEDED, attempts);
            if (candidate.isEmpty()) break;

            var choice = candidate.get();
            var model = choice.model();
            long attemptStart = System.nanoTime();
            reserved = reserved.add(choice.reservedCostUsd());
            Provider.Reply reply = null;
            RouteFailure.Code failure = null;
            try {
                reply = call(model, request, start, allowance);
                if (reply == null || reply.output() == null || reply.output().isBlank() ||
                        (request.outputSchema() != null &&
                                !schemaValidator.test(request.outputSchema(), reply.output()))) {
                    failure = INVALID_OUTPUT;
                }
            } catch (RouteFailure error) {
                failure = error.code();
            } catch (RuntimeException error) {
                failure = INVALID_OUTPUT;
            }
            if (remaining(start, allowance) <= 0) failure = DEADLINE_EXCEEDED;
            var usage = reply == null ? null : reply.usage();
            var charge = usage == null ? null : model.cost(usage.inputTokens(), usage.outputTokens());
            actual = actual == null || charge == null ? null : actual.add(charge);
            budget = budget.subtract(charge == null ? choice.reservedCostUsd() :
                    choice.reservedCostUsd().max(charge));
            attempts.add(new RouteResult.Attempt(model.alias(), choice.reservedCostUsd(),
                    charge, usage, elapsedMs(attemptStart), failure));
            if (failure == null) {
                long elapsed = elapsedMs(start);
                return new RouteResult(reply.output(), model.alias(), policy.version(),
                        "Lowest reserved cost among eligible remaining models",
                        reserved, actual, elapsed, attempts, true, elapsed > request.targetLatencyMs());
            }
            lastFailure = failure;
            if (failure == DEADLINE_EXCEEDED || Thread.currentThread().isInterrupted()) break;
        }
        throw new RouteFailure(lastFailure, attempts);
    }

    private static Provider.Reply call(ModelRegistry.Model model, RouteRequest request,
                                      long start, long allowance) {
        var job = new FutureTask<>(() -> {
            long remaining = remaining(start, allowance);
            if (remaining <= 0) throw new RouteFailure(DEADLINE_EXCEEDED);
            return model.provider().invoke(model.providerModelId(), request, Duration.ofNanos(remaining));
        });
        long remaining = remaining(start, allowance);
        if (remaining <= 0) throw new RouteFailure(DEADLINE_EXCEEDED);
        var worker = new Thread(job, "router-provider");
        worker.setDaemon(true);
        worker.start();
        try {
            return job.get(Math.max(0, remaining(start, allowance)), TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            job.cancel(true);
            throw new RouteFailure(DEADLINE_EXCEEDED);
        } catch (InterruptedException error) {
            job.cancel(true);
            Thread.currentThread().interrupt();
            throw new RouteFailure(PROVIDER_FAILURE);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RouteFailure failure &&
                    failure.code() == DEADLINE_EXCEEDED) {
                throw new RouteFailure(DEADLINE_EXCEEDED);
            }
            throw new RouteFailure(PROVIDER_FAILURE);
        }
    }

    private static long remaining(long start, long allowance) {
        return allowance - (System.nanoTime() - start);
    }

    private static long elapsedMs(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
