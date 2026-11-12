package router;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

public final class ModelRegistry {
    private final List<Model> models;

    public ModelRegistry(Collection<Model> models) {
        var byAlias = new LinkedHashMap<String, Model>();
        for (Model model : models) {
            if (byAlias.putIfAbsent(model.alias(), model) != null) {
                throw new IllegalArgumentException("Duplicate model alias");
            }
        }
        this.models = List.copyOf(byAlias.values());
    }

    public List<Model> models() {
        return models;
    }

    public record Model(String alias, String providerModelId, Provider provider,
                        long contextTokens, boolean structuredOutput,
                        BigDecimal inputUsdPerMillion, BigDecimal outputUsdPerMillion,
                        ToLongFunction<RouteRequest> inputTokenUpperBound) {
        public Model {
            if (alias == null || alias.isBlank() || providerModelId == null ||
                    providerModelId.isBlank() || contextTokens <= 0 ||
                    (inputUsdPerMillion == null) != (outputUsdPerMillion == null) ||
                    (inputUsdPerMillion != null &&
                     (inputUsdPerMillion.signum() < 0 || outputUsdPerMillion.signum() < 0))) {
                throw new IllegalArgumentException("Invalid model configuration");
            }
            Objects.requireNonNull(inputTokenUpperBound);
        }

        public BigDecimal cost(long inputTokens, long outputTokens) {
            if (inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException("Negative token count");
            }
            if (inputUsdPerMillion == null) return null;
            return inputUsdPerMillion.multiply(BigDecimal.valueOf(inputTokens))
                    .add(outputUsdPerMillion.multiply(BigDecimal.valueOf(outputTokens)))
                    .movePointLeft(6);
        }
    }
}
