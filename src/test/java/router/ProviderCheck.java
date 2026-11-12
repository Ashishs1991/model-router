package router;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Set;

public final class ProviderCheck {
    private static final RouteRequest REQUEST = new RouteRequest("classify", "hello", "standard",
            BigDecimal.ONE, 1000, 1500, 20, null, Set.of());
    private static final Duration TIME = Duration.ofMillis(1200);

    public static void main(String[] args) {
        var openAi = new HttpProvider(HttpProvider.Protocol.OPENAI_CHAT, () -> "fixture-secret", http -> {
            if (!http.uri().getPath().equals("/v1/chat/completions") ||
                    !http.headers().firstValue("Authorization").orElseThrow().equals("Bearer fixture-secret") ||
                    !http.timeout().orElseThrow().equals(TIME)) throw new AssertionError("Unexpected request");
            return new HttpProvider.Response(200, """
                    {"choices":[{"message":{"content":"billing"}}],
                     "usage":{"prompt_tokens":12,"completion_tokens":3}}
                    """);
        });
        var result = openAi.invoke("fixture", REQUEST, TIME);
        if (!result.output().equals("billing") || !new Provider.Usage(12, 3).equals(result.usage())) {
            throw new AssertionError("OpenAI usage or output did not normalize");
        }
        var anthropic = new HttpProvider(HttpProvider.Protocol.ANTHROPIC_MESSAGES, () -> "fixture-secret", http -> {
            if (!http.uri().getPath().equals("/v1/messages") ||
                    !http.headers().firstValue("x-api-key").orElseThrow().equals("fixture-secret")) {
                throw new AssertionError("Unexpected request");
            }
            return new HttpProvider.Response(200, """
                    {"content":[{"type":"text","text":"bill"},{"type":"text","text":"ing"}]}
                    """);
        });
        result = anthropic.invoke("fixture", REQUEST, TIME);
        if (!result.output().equals("billing") || result.usage() != null) {
            throw new AssertionError("Anthropic output or unknown usage did not normalize");
        }
        expect(http -> new HttpProvider.Response(429, "fixture-secret"), RouteFailure.Code.PROVIDER_FAILURE);
        expect(http -> new HttpProvider.Response(200, "{bad"), RouteFailure.Code.PROVIDER_FAILURE);
        expect(http -> { throw new HttpTimeoutException("fixture-secret"); }, RouteFailure.Code.DEADLINE_EXCEEDED);
        System.out.println("Provider checks passed");
    }

    private static void expect(HttpProvider.Transport transport, RouteFailure.Code code) {
        try {
            new HttpProvider(HttpProvider.Protocol.OPENAI_CHAT, () -> "fixture-secret", transport)
                    .invoke("fixture", REQUEST, TIME);
        } catch (RouteFailure failure) {
            if (failure.code() == code && failure.getCause() == null &&
                    failure.getMessage().equals(code.name())) return;
            throw new AssertionError("Unexpected or unredacted provider failure");
        }
        throw new AssertionError("Expected " + code);
    }
}
