package router;

import java.math.BigDecimal;
import java.util.Set;

public record RouteRequest(String task, String input, String qualityProfile, BigDecimal maxCostUsd,
                           long targetLatencyMs, long timeoutMs, int maxOutputTokens,
                           String outputSchema, Set<String> allowedModels) {
    public void validate() {
        if (blank(task) || blank(input) || blank(qualityProfile) ||
                maxCostUsd == null || maxCostUsd.signum() < 0 ||
                targetLatencyMs <= 0 || timeoutMs <= 0 || maxOutputTokens <= 0 ||
                (outputSchema != null && outputSchema.isBlank()) ||
                allowedModels == null || allowedModels.stream().anyMatch(RouteRequest::blank)) {
            throw new RouteFailure(RouteFailure.Code.INVALID_REQUEST);
        }
    }

    public RouteRequest {
        if (allowedModels != null && allowedModels.stream().noneMatch(RouteRequest::blank)) {
            allowedModels = Set.copyOf(allowedModels);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
