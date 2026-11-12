package router;

import java.util.List;

public final class RouteFailure extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, UNKNOWN_TASK_PROFILE, NO_ELIGIBLE_MODEL,
        DEADLINE_EXCEEDED, PROVIDER_FAILURE, INVALID_OUTPUT
    }

    private final Code code;
    private final List<RouteResult.Attempt> attempts;

    public RouteFailure(Code code) {
        this(code, List.of());
    }

    public RouteFailure(Code code, List<RouteResult.Attempt> attempts) {
        super(code.name());
        this.code = code;
        this.attempts = List.copyOf(attempts);
    }

    public List<RouteResult.Attempt> attempts() {
        return attempts;
    }

    public Code code() {
        return code;
    }
}
