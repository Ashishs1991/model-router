package router;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static router.Evaluation.Split.*;
import static router.Evaluation.Task.*;

public final class EvaluationCheck {
    public static void main(String[] args) {
        var dev = example("d", "one", DEVELOPMENT, CLASSIFICATION, "a");
        var cal = example("c", "two", CALIBRATION, CLASSIFICATION, "b");
        var test = example("t", "three", TEST, CLASSIFICATION, "a");
        var extract = example("x", "four", TEST, EXTRACTION, "{\"id\":1,\"name\":\"A\"}");
        var data = List.of(dev, cal, test, extract);
        Evaluation.verifySplits(data);
        try {
            Evaluation.verifySplits(List.of(dev, cal, example("t", "one", TEST, CLASSIFICATION, "a")));
            throw new AssertionError("Near duplicate group leaked");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("leakage")) throw expected;
        }

        var calls = new AtomicInteger();
        var result = Evaluation.run(data, List.of(new Evaluation.Policy("fixture", request -> {
            calls.incrementAndGet();
            if (request.input().equals("three")) return new RouteResult("a", "fixture", "test", "fixture",
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of(), true, false);
            throw new RouteFailure(RouteFailure.Code.PROVIDER_FAILURE);
        })), 2);
        if (calls.get() != 4 || Evaluation.summarize(result.get("fixture")).failures() != 2) {
            throw new AssertionError("Only held-out cases should run and failures should count");
        }
        var rows = List.of(
                new Evaluation.Observation(test, "a", true, false, false, false, 10, BigDecimal.ONE),
                new Evaluation.Observation(extract, "{\"id\":1,\"name\":\"B\"}", true,
                        false, true, false, 30, BigDecimal.ONE));
        var metrics = Evaluation.summarize(rows);
        if (metrics.successRate() != .5 || metrics.fieldAccuracy() != .5 ||
                metrics.macroF1() != 1 || metrics.schemaValidity() != 1 ||
                metrics.p50Ms() != 10 || metrics.p95Ms() != 30 ||
                metrics.costPerSuccess().compareTo(BigDecimal.valueOf(2)) != 0 ||
                metrics.fallbacks() != 1 || metrics.success95().low() >= .5 ||
                metrics.success95().high() <= .5) {
            throw new AssertionError("Unexpected metrics: " + metrics);
        }
        var unknown = new Evaluation.Observation(test, null, false, true, false, false, 50, null);
        if (Evaluation.summarize(List.of(unknown)).averageCost() != null) {
            throw new AssertionError("Unknown attempt costs must not be reported as zero");
        }
        System.out.println("Evaluation checks passed");
    }

    private static Evaluation.Example example(String id, String group, Evaluation.Split split,
                                              Evaluation.Task task, String expected) {
        return new Evaluation.Example(id, group, split, task,
                new RouteRequest(task.name(), group, "standard", BigDecimal.TEN, 100, 200,
                        20, null, Set.of()), expected);
    }
}
