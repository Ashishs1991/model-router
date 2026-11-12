package router;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public final class ContractCheck {
    public static void main(String[] args) {
        var profiles = new TaskProfiles(Map.of("classify", Set.of("standard")));
        var request = request("standard", BigDecimal.ZERO);
        profiles.validate(request);
        expect(RouteFailure.Code.INVALID_REQUEST, () -> profiles.validate(null));
        expect(RouteFailure.Code.INVALID_REQUEST,
                () -> profiles.validate(request("standard", BigDecimal.ONE.negate())));
        expect(RouteFailure.Code.UNKNOWN_TASK_PROFILE,
                () -> profiles.validate(request("high", BigDecimal.ONE)));
        Provider fake = (model, input, remaining) -> new Provider.Reply("billing", null);
        if (fake.invoke("fixture", request, Duration.ofSeconds(1)).usage() != null) {
            throw new AssertionError("Missing provider usage must remain unknown");
        }
        System.out.println("Contract checks passed");
    }

    private static RouteRequest request(String profile, BigDecimal budget) {
        return new RouteRequest("classify", "Invoice question", profile, budget,
                500, 1000, 30, null, Set.of());
    }

    private static void expect(RouteFailure.Code code, Runnable operation) {
        try {
            operation.run();
        } catch (RouteFailure failure) {
            if (failure.code() == code) return;
            throw new AssertionError("Unexpected failure", failure);
        }
        throw new AssertionError("Expected " + code);
    }
}
