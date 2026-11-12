package router;

public final class RouteFailure extends RuntimeException {
    public enum Code {
        INVALID_REQUEST, UNKNOWN_TASK_PROFILE, NO_ELIGIBLE_MODEL,
        DEADLINE_EXCEEDED, PROVIDER_FAILURE, INVALID_OUTPUT
    }

    private final Code code;

    public RouteFailure(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
