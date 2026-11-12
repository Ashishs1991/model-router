package router;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PolicyCheck {
    private static final Provider FAKE = (id, req, time) -> new Provider.Reply("ok", null);

    public static void main(String[] args) {
        var a = model("a", 100, true, BigDecimal.ONE);
        var b = model("b", 100, true, BigDecimal.ONE);
        var tiny = model("tiny", 19, true, BigDecimal.ZERO);
        var plain = model("plain", 100, false, BigDecimal.ZERO);
        var unknown = model("unknown", 100, true, null);
        var registry = new ModelRegistry(List.of(b, plain, unknown, tiny, a,
                model("unmeasured", 100, true, BigDecimal.ZERO)));
        var evidence = Map.of(key("a"), pass(), key("b"), pass(), key("tiny"), pass(),
                key("plain"), pass(), key("unknown"), pass());
        var policy = new RoutingPolicy("synthetic-v1", evidence);
        var budget = new BigDecimal("0.000020");
        var selected = policy.candidates(registry, request(budget, Set.of()), budget, 500);
        if (!selected.stream().map(c -> c.model().alias()).toList().equals(List.of("a", "b"))) {
            throw new AssertionError("Expected capability filtering and deterministic tie order");
        }
        var lower = new BigDecimal("0.000019");
        if (!policy.candidates(registry, request(lower, Set.of()), lower, 500).isEmpty() ||
                !policy.candidates(registry, request(budget, Set.of("missing")), budget, 500).isEmpty() ||
                !policy.candidates(registry, request(budget, Set.of()), budget, 99).isEmpty()) {
            throw new AssertionError("Budget, allowlist or latency filtering failed");
        }
        if (a.cost(10, 10).compareTo(budget) != 0 || unknown.cost(10, 10) != null) {
            throw new AssertionError("Cost arithmetic or unknown pricing failed");
        }
        try {
            new ModelRegistry(List.of(a, a));
            throw new AssertionError("Duplicate aliases accepted");
        } catch (IllegalArgumentException expected) {
            // The registry must identify each candidate uniquely.
        }
        System.out.println("Policy checks passed");
    }

    private static RouteRequest request(BigDecimal budget, Set<String> allowed) {
        return new RouteRequest("extract", "invoice", "standard", budget, 500, 500, 10, "{}", allowed);
    }

    private static ModelRegistry.Model model(String alias, long context, boolean json, BigDecimal rate) {
        return new ModelRegistry.Model(alias, alias, FAKE, context, json, rate, rate, request -> 10);
    }

    private static RoutingPolicy.Key key(String alias) {
        return new RoutingPolicy.Key("extract", "standard", alias);
    }

    private static RoutingPolicy.Evidence pass() {
        return new RoutingPolicy.Evidence(true, 100);
    }
}
