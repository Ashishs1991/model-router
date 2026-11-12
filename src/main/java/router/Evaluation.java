package router;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class Evaluation {
    private static final ObjectMapper JSON = new ObjectMapper();
    public enum Split { DEVELOPMENT, CALIBRATION, TEST }
    public enum Task { CLASSIFICATION, EXTRACTION }

    public record Example(String id, String group, Split split, Task task, RouteRequest request,
                          String expected) {}

    public record Policy(String name, Function<RouteRequest, RouteResult> run) {}

    public record Observation(Example example, String prediction, boolean valid, boolean failed,
                              boolean fallback, boolean violation, long latencyMs, BigDecimal cost) {}

    public record Interval(double low, double high) {}

    public record Metrics(int samples, double successRate, Interval success95,
                          double macroF1, double fieldAccuracy, double schemaValidity,
                          int failures, int fallbacks, int violations, long p50Ms, long p95Ms,
                          BigDecimal averageCost, BigDecimal costPerSuccess, int unknownCosts) {}

    private Evaluation() {}

    public static void verifySplits(List<Example> examples) {
        var groups = new HashMap<String, Split>();
        var inputs = new HashMap<String, Split>();
        var ids = new HashSet<String>();
        var splits = new HashSet<Split>();
        for (var example : examples) {
            if (example.id().isBlank() || example.group().isBlank() || !ids.add(example.id())) {
                throw new IllegalArgumentException("Invalid or duplicate example identifier");
            }
            var split = example.split();
            if (split == null || example.task() == null || example.expected().isBlank()) {
                throw new IllegalArgumentException("Missing dataset metadata");
            }
            example.request().validate();
            if (example.task() == Task.EXTRACTION && fields(example.expected()) == null) {
                throw new IllegalArgumentException("Extraction labels must be JSON objects");
            }
            splits.add(split);
            var input = example.request().input().strip().toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ");
            bind(groups, example.group(), split);
            bind(inputs, input, split);
        }
        if (splits.size() != Split.values().length) {
            throw new IllegalArgumentException("All dataset splits are required");
        }
    }

    private static void bind(Map<String, Split> groups, String key, Split split) {
        var previous = groups.putIfAbsent(key, split);
        if (previous != null && previous != split) {
            throw new IllegalArgumentException("Dataset leakage across splits");
        }
    }

    public static Map<String, List<Observation>> run(List<Example> examples, List<Policy> policies,
                                                      int repetitions) {
        verifySplits(examples);
        if (repetitions <= 0 || policies.isEmpty()) throw new IllegalArgumentException("Empty benchmark");
        var results = new java.util.LinkedHashMap<String, List<Observation>>();
        for (var policy : policies) {
            if (policy.name().isBlank() || results.putIfAbsent(policy.name(), new ArrayList<>()) != null) {
                throw new IllegalArgumentException("Invalid or duplicate policy name");
            }
        }
        int rotation = 0;
        for (int repeat = 0; repeat < repetitions; repeat++) {
            for (var example : examples) {
                if (example.split() != Split.TEST) continue;
                for (int i = 0; i < policies.size(); i++) {
                    var policy = policies.get((rotation + i) % policies.size());
                    results.get(policy.name()).add(observe(example, policy));
                }
                rotation = (rotation + 1) % policies.size();
            }
        }
        results.replaceAll((name, rows) -> List.copyOf(rows));
        return java.util.Collections.unmodifiableMap(results);
    }

    private static Observation observe(Example example, Policy policy) {
        long start = System.nanoTime();
        try {
            var result = policy.run().apply(example.request());
            return row(example, result.output(), result.validated(), false, result.attempts(), start);
        } catch (RouteFailure failure) {
            return row(example, null, false, true, failure.attempts(), start);
        }
    }

    private static Observation row(Example example, String output, boolean valid, boolean failed,
                                   List<RouteResult.Attempt> attempts, long start) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        BigDecimal cost = BigDecimal.ZERO;
        boolean violation = false;
        for (var attempt : attempts) {
            if (attempt.actualCostUsd() == null) {
                cost = null;
            } else if (cost != null) {
                cost = cost.add(attempt.actualCostUsd());
            }
            var allowed = example.request().allowedModels();
            violation |= !allowed.isEmpty() && !allowed.contains(attempt.modelAlias());
        }
        violation |= cost != null && cost.compareTo(example.request().maxCostUsd()) > 0;
        valid &= example.task() != Task.EXTRACTION || fields(output) != null;
        return new Observation(example, output, valid, failed, attempts.size() > 1, violation, elapsed, cost);
    }

    public static Metrics summarize(List<Observation> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("No observations");
        int successes = 0, failures = 0, fallbacks = 0, violations = 0, unknown = 0;
        int expectedFields = 0, correctFields = 0, extraction = 0, validExtraction = 0;
        BigDecimal cost = BigDecimal.ZERO;
        var classes = new HashSet<String>();
        for (var row : rows) {
            if (row.failed()) failures++;
            if (row.fallback()) fallbacks++;
            if (row.violation()) violations++;
            if (row.cost() == null) unknown++;
            else cost = cost.add(row.cost());
            if (row.example().task() == Task.CLASSIFICATION) {
                classes.add(row.example().expected());
                if (row.valid() && row.example().expected().equals(row.prediction())) successes++;
            } else {
                extraction++;
                if (row.valid()) validExtraction++;
                var expected = fields(row.example().expected());
                var actual = row.valid() ? fields(row.prediction()) : null;
                expectedFields += expected.size();
                var names = expected.fieldNames();
                while (names.hasNext()) {
                    var name = names.next();
                    if (actual != null && expected.get(name).equals(actual.get(name))) correctFields++;
                }
                if (expected.equals(actual)) successes++;
            }
        }
        double f1 = classes.stream().mapToDouble(label -> f1(rows, label)).average().orElse(Double.NaN);
        var latencies = rows.stream().mapToLong(Observation::latencyMs).sorted().toArray();
        int n = rows.size();
        return new Metrics(n, ratio(successes, n), wilson(successes, n), f1,
                ratio(correctFields, expectedFields), ratio(validExtraction, extraction),
                failures, fallbacks, violations, percentile(latencies, .50), percentile(latencies, .95),
                unknown == 0 ? divide(cost, n) : null,
                unknown == 0 && successes > 0 ? divide(cost, successes) : null, unknown);
    }

    private static double f1(List<Observation> rows, String label) {
        int tp = 0, fp = 0, fn = 0;
        for (var row : rows) {
            if (row.example().task() != Task.CLASSIFICATION) continue;
            boolean expected = label.equals(row.example().expected());
            boolean predicted = row.valid() && label.equals(row.prediction());
            if (expected && predicted) tp++;
            else if (predicted) fp++;
            else if (expected) fn++;
        }
        return ratio(2.0 * tp, 2.0 * tp + fp + fn);
    }

    private static JsonNode fields(String output) {
        if (output == null) return null;
        try {
            var node = JSON.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(output);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException error) {
            return null;
        }
    }

    private static long percentile(long[] sorted, double p) {
        return sorted[(int) Math.ceil(sorted.length * p) - 1];
    }

    private static double ratio(double numerator, double denominator) {
        return denominator == 0 ? Double.NaN : numerator / denominator;
    }

    private static BigDecimal divide(BigDecimal total, int n) {
        return total.divide(BigDecimal.valueOf(n), MathContext.DECIMAL64);
    }

    private static Interval wilson(int success, int n) {
        double z = 1.96, p = ratio(success, n), factor = 1 + z * z / n;
        double center = (p + z * z / (2 * n)) / factor;
        double half = z * Math.sqrt((p * (1 - p) + z * z / (4 * n)) / n) / factor;
        return new Interval(Math.max(0, center - half), Math.min(1, center + half));
    }
}
