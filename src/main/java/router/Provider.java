package router;

import java.time.Duration;

@FunctionalInterface
public interface Provider {
    Reply invoke(String modelId, RouteRequest request, Duration remaining);

    record Reply(String output, Usage usage) {}

    record Usage(long inputTokens, long outputTokens) {
        public Usage {
            if (inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException("Token counts must be nonnegative");
            }
        }
    }
}
