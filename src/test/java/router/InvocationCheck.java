package router;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class InvocationCheck {
    public static void main(String[] args) {
        var calls = new AtomicInteger();
        Provider broken = (id, req, time) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("secret must never leak");
        };
        Provider good = (id, req, time) -> {
            calls.incrementAndGet();
            return new Provider.Reply("ok", new Provider.Usage(1, 1));
        };
        var router = router(broken, good, true);
        var result = router.invoke(request("0.000004", 1000, Set.of()));
        if (!result.modelAlias().equals("b") || result.attempts().size() != 2 ||
                result.actualCostUsd() != null || calls.get() != 2 ||
                result.estimatedCostUsd().compareTo(new BigDecimal("0.000004")) != 0) {
            throw new AssertionError("Fallback and cost accounting failed");
        }
        expect(router, request("0.000003", 1000, Set.of()), RouteFailure.Code.PROVIDER_FAILURE);
        expect(router, request("0.000004", 1000, Set.of("a")), RouteFailure.Code.PROVIDER_FAILURE);
        expect(router(broken, good, false), request("0.000004", 1000, Set.of()),
                RouteFailure.Code.PROVIDER_FAILURE);
        Provider blocked = (id, req, time) -> {
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Latch should remain closed");
            } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
                return new Provider.Reply("cancelled", null);
            }
        };
        expect(router(blocked, good, true), request("0.000004", 200, Set.of()),
                RouteFailure.Code.DEADLINE_EXCEEDED);
        expect(router((id, req, time) -> new Provider.Reply("bad", null), good, true),
                request("0.000004", 1000, Set.of("a")), RouteFailure.Code.INVALID_OUTPUT);
        var slowValidator = router(good, good, true, (schema, output) -> {
            blocked.invoke("", null, java.time.Duration.ZERO);
            return true;
        });
        expect(slowValidator, request("0.000004", 200, Set.of()), RouteFailure.Code.DEADLINE_EXCEEDED);
        System.out.println("Invocation checks passed");
    }

    private static void expect(ModelRouter router, RouteRequest request, RouteFailure.Code code) {
        try {
            router.invoke(request);
        } catch (RouteFailure error) {
            if (error.code() != code || error.attempts().size() != 1 ||
                    error.getCause() != null || !error.getMessage().equals(code.name())) {
                throw new AssertionError("Unexpected routing failure", error);
            }
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private static RouteRequest request(String budget, int timeout, Set<String> allowed) {
        return new RouteRequest("extract", "x", "standard", new BigDecimal(budget),
                1000, timeout, 1, "fixture", allowed);
    }

    private static ModelRouter router(Provider a, Provider b, boolean fallback) {
        return router(a, b, fallback, (schema, output) -> output.equals("ok"));
    }

    private static ModelRouter router(Provider a, Provider b, boolean fallback,
                                      java.util.function.BiPredicate<String, String> validator) {
        var registry = new ModelRegistry(List.of(model("a", a), model("b", b)));
        var evidence = new RoutingPolicy.Evidence(true, 1);
        var policy = new RoutingPolicy("fixture", Map.of(
                new RoutingPolicy.Key("extract", "standard", "a"), evidence,
                new RoutingPolicy.Key("extract", "standard", "b"), evidence));
        return new ModelRouter(new TaskProfiles(Map.of("extract", Set.of("standard"))),
                registry, policy, validator, fallback);
    }

    private static ModelRegistry.Model model(String alias, Provider provider) {
        return new ModelRegistry.Model(alias, alias, provider, 100, true,
                BigDecimal.ONE, BigDecimal.ONE, request -> 1);
    }
}
