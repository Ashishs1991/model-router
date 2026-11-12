package router;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static router.Evaluation.Split.*;
import static router.Evaluation.Task.*;

public final class Sample {
    private static final String SCHEMA = """
            {"type":"object","properties":{"reference":{"type":"string"}},
             "required":["reference"],"additionalProperties":false}
            """;
    private static final String VERSION = "offline-fixtures-v1";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final TaskProfiles TASKS = new TaskProfiles(
            Map.of("classify", Set.of("standard"), "extract", Set.of("standard")));

    private Sample() {}

    public static void main(String[] args) {
        var data = dataset();
        var models = List.of(model("economy-fixture", "1", false), model("quality-fixture", "4", true));
        var route = router(models, true);
        if (args.length == 0) {
            var request = data.get(7).request();
            var result = route.invoke(request);
            System.out.printf("Task: %s%nModel: %s%nReason: %s%nOutput: %s%nEstimated USD: %s%n" +
                            "Fixture USD: %s%nElapsed ms: %d%nAttempts: %d%n",
                    request.task(), result.modelAlias(), result.reason(), result.output(),
                    result.estimatedCostUsd(), result.actualCostUsd(), result.elapsedMs(), result.attempts().size());
            return;
        }
        if (args.length != 1 || !args[0].equals("--pilot")) {
            throw new IllegalArgumentException("Only --pilot is supported");
        }

        var fixedEconomy = router(List.of(models.get(0)), false);
        var fixedQuality = router(List.of(models.get(1)), false);
        var policies = List.of(
                new Evaluation.Policy("routed", route::invoke),
                new Evaluation.Policy("fixed-economy", fixedEconomy::invoke),
                new Evaluation.Policy("fixed-quality", fixedQuality::invoke),
                new Evaluation.Policy("task-mapping", request ->
                        (request.task().equals("classify") ? fixedEconomy : fixedQuality).invoke(request)));
        System.out.println("Synthetic offline pilot " + VERSION + " (serial, one run per held-out case)");
        Evaluation.run(data, policies, 1).forEach((name, rows) -> {
            System.out.println(name + " overall " + Evaluation.summarize(rows));
            for (var task : Evaluation.Task.values()) {
                var subset = rows.stream().filter(row -> row.example().task() == task).toList();
                System.out.println(name + " " + task + " " + Evaluation.summarize(subset));
            }
        });
    }

    static List<Evaluation.Example> dataset() {
        return List.of(
                example("dev-1", DEVELOPMENT, CLASSIFICATION, "Replace broken lamp", "returns"),
                example("dev-2", DEVELOPMENT, EXTRACTION, "Booking reference HOTEL-100", json("HOTEL-100")),
                example("cal-1", CALIBRATION, CLASSIFICATION, "Receipt for last month", "billing"),
                example("cal-2", CALIBRATION, EXTRACTION, "Shipment reference SHIP-200", json("SHIP-200")),
                example("test-1", TEST, CLASSIFICATION, "Invoice charged twice", "billing"),
                example("test-2", TEST, CLASSIFICATION, "The receipt is fine but I need to return these shoes", "returns"),
                example("test-3", TEST, EXTRACTION, "Invoice reference INV-482", json("INV-482")),
                example("test-4", TEST, EXTRACTION, "Old PO-20 void. Use PO-21 instead", json("PO-21")));
    }

    private static Evaluation.Example example(String id, Evaluation.Split split, Evaluation.Task task,
                                               String input, String expected) {
        var request = new RouteRequest(task == CLASSIFICATION ? "classify" : "extract",
                input, "standard", new BigDecimal("0.01"), 1000, 2000, 40,
                task == EXTRACTION ? SCHEMA : null, Set.of());
        return new Evaluation.Example(id, id, split, task, request, expected);
    }

    private static ModelRegistry.Model model(String alias, String rate, boolean careful) {
        Provider fixture = (id, request, remaining) -> {
            var output = switch (request.input()) {
                case "Invoice charged twice" -> "billing";
                case "The receipt is fine but I need to return these shoes" -> careful ? "returns" : "billing";
                case "Invoice reference INV-482" -> json("INV-482");
                case "Old PO-20 void. Use PO-21 instead" -> careful ? json("PO-21") : "{}";
                default -> throw new RouteFailure(RouteFailure.Code.PROVIDER_FAILURE);
            };
            return new Provider.Reply(output, new Provider.Usage(30, 5));
        };
        return new ModelRegistry.Model(alias, alias, fixture, 1000, true,
                new BigDecimal(rate), new BigDecimal(rate), request -> 300);
    }

    private static ModelRouter router(List<ModelRegistry.Model> models, boolean fallback) {
        var synthetic = new RoutingPolicy.Evidence(true, 25);
        var policy = new RoutingPolicy(VERSION, Map.of(
                new RoutingPolicy.Key("classify", "standard", "economy-fixture"), synthetic,
                new RoutingPolicy.Key("classify", "standard", "quality-fixture"), synthetic,
                new RoutingPolicy.Key("extract", "standard", "economy-fixture"), synthetic,
                new RoutingPolicy.Key("extract", "standard", "quality-fixture"), synthetic));
        return new ModelRouter(TASKS, new ModelRegistry(models), policy, Sample::validateFixtureSchema, fallback);
    }

    private static boolean validateFixtureSchema(String schema, String output) {
        if (!SCHEMA.equals(schema)) return false;
        try {
            var document = JSON.readTree(output);
            return document != null && document.isObject() && document.size() == 1 &&
                    document.path("reference").isTextual();
        } catch (JsonProcessingException error) {
            return false;
        }
    }

    private static String json(String reference) {
        return "{\"reference\":\"" + reference + "\"}";
    }
}
