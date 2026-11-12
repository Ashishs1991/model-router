package router;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class RoutingPolicy {
    public record Key(String task, String profile, String modelAlias) {}

    public record Evidence(boolean meetsQuality, long estimatedLatencyMs) {
        public Evidence {
            if (estimatedLatencyMs <= 0) {
                throw new IllegalArgumentException("Latency estimate must be positive");
            }
        }
    }

    public record Candidate(ModelRegistry.Model model, BigDecimal reservedCostUsd) {}

    private final String version;
    private final Map<Key, Evidence> evidence;

    public RoutingPolicy(String version, Map<Key, Evidence> evidence) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Policy version is required");
        }
        this.version = version;
        this.evidence = Map.copyOf(evidence);
    }

    public String version() {
        return version;
    }

    public List<Candidate> candidates(ModelRegistry registry, RouteRequest request,
                                      BigDecimal remainingBudget, long remainingMs) {
        var eligible = new ArrayList<Candidate>();
        for (var model : registry.models()) {
            if (model.provider() == null ||
                    (!request.allowedModels().isEmpty() && !request.allowedModels().contains(model.alias())) ||
                    (request.outputSchema() != null && !model.structuredOutput())) continue;

            var measurement = evidence.get(new Key(request.task(), request.qualityProfile(), model.alias()));
            if (measurement == null || !measurement.meetsQuality() ||
                    measurement.estimatedLatencyMs() > Math.min(request.targetLatencyMs(), remainingMs)) continue;

            long inputBound = model.inputTokenUpperBound().applyAsLong(request);
            if (inputBound < 0 || inputBound > model.contextTokens() - request.maxOutputTokens()) continue;

            BigDecimal cost = model.cost(inputBound, request.maxOutputTokens());
            if (cost != null && cost.compareTo(remainingBudget) <= 0) {
                eligible.add(new Candidate(model, cost));
            }
        }
        eligible.sort(Comparator.comparing(Candidate::reservedCostUsd)
                .thenComparing(candidate -> candidate.model().alias()));
        return List.copyOf(eligible);
    }
}
