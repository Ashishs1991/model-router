package router;

import java.math.BigDecimal;
import java.util.List;

public record RouteResult(String output, String modelAlias, String policyVersion,
                          String reason, BigDecimal estimatedCostUsd, BigDecimal actualCostUsd,
                          long elapsedMs, List<Attempt> attempts, boolean validated,
                          boolean missedLatencyTarget) {
    public RouteResult {
        attempts = List.copyOf(attempts);
    }

    public record Attempt(String modelAlias, BigDecimal estimatedCostUsd, BigDecimal actualCostUsd,
                          Provider.Usage usage, long elapsedMs, RouteFailure.Code failure) {}
}
