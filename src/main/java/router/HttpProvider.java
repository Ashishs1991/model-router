package router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import static router.RouteFailure.Code.*;

public final class HttpProvider implements Provider {
    public enum Protocol {
        OPENAI_CHAT("https://api.openai.com/v1/chat/completions"),
        ANTHROPIC_MESSAGES("https://api.anthropic.com/v1/messages");

        private final URI endpoint;

        Protocol(String endpoint) {
            this.endpoint = URI.create(endpoint);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Protocol protocol;
    private final Supplier<String> credential;
    private final Transport transport;

    public HttpProvider(Protocol protocol, Supplier<String> credential) {
        this(protocol, credential, transport());
    }

    HttpProvider(Protocol protocol, Supplier<String> credential, Transport transport) {
        this.protocol = Objects.requireNonNull(protocol);
        this.credential = Objects.requireNonNull(credential);
        this.transport = Objects.requireNonNull(transport);
    }

    @Override
    public Reply invoke(String modelId, RouteRequest request, Duration remaining) {
        try {
            if (remaining.isZero() || remaining.isNegative()) {
                throw new HttpTimeoutException("Deadline expired");
            }
            String key = credential.get();
            if (key == null || key.isBlank()) throw new IllegalStateException();
            var body = JSON.createObjectNode().put("model", modelId);
            String input = request.input();
            if (request.outputSchema() != null) {
                input += "\n\nReturn only JSON matching this schema:\n" + request.outputSchema();
            }
            body.putArray("messages").addObject().put("role", "user").put("content", input);
            var builder = HttpRequest.newBuilder(protocol.endpoint).timeout(remaining)
                    .header("Content-Type", "application/json");
            boolean openAi = protocol == Protocol.OPENAI_CHAT;
            if (openAi) {
                builder.header("Authorization", "Bearer " + key);
                body.put("max_completion_tokens", request.maxOutputTokens());
            } else {
                builder.header("x-api-key", key).header("anthropic-version", "2023-06-01");
                body.put("max_tokens", request.maxOutputTokens());
            }
            var response = transport.send(builder.POST(
                    HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build());
            if (response.status() < 200 || response.status() >= 300) throw new IOException();
            JsonNode root = JSON.readTree(response.body());
            String output = openAi ? root.path("choices").path(0).path("message")
                    .path("content").textValue() : anthropicText(root.path("content"));
            if (output == null || output.isBlank()) throw new IOException();
            JsonNode usage = root.path("usage");
            String inputField = openAi ? "prompt_tokens" : "input_tokens";
            String outputField = openAi ? "completion_tokens" : "output_tokens";
            return new Reply(output, usage(usage, inputField, outputField));
        } catch (HttpTimeoutException error) {
            throw new RouteFailure(DEADLINE_EXCEEDED);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RouteFailure(PROVIDER_FAILURE);
        } catch (IOException | RuntimeException error) {
            throw new RouteFailure(PROVIDER_FAILURE);
        }
    }

    private static Usage usage(JsonNode usage, String inputField, String outputField) throws IOException {
        if (usage.isMissingNode() || usage.isNull()) return null;
        var input = usage.path(inputField);
        var output = usage.path(outputField);
        if (input.isMissingNode() || output.isMissingNode()) return null;
        if (!input.isIntegralNumber() || !input.canConvertToLong() ||
                !output.isIntegralNumber() || !output.canConvertToLong()) throw new IOException();
        if (usage.path("cache_creation_input_tokens").asLong() != 0 ||
                usage.path("cache_read_input_tokens").asLong() != 0) return null;
        return new Usage(input.longValue(), output.longValue());
    }

    private static String anthropicText(JsonNode content) {
        var output = new StringBuilder();
        for (var block : content) {
            if (block.path("type").asText().equals("text") && block.path("text").isTextual()) {
                output.append(block.path("text").textValue());
            }
        }
        return output.toString();
    }

    private static Transport transport() {
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return request -> {
            var reply = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(reply.statusCode(), reply.body());
        };
    }

    @FunctionalInterface
    interface Transport {
        Response send(HttpRequest request) throws IOException, InterruptedException;
    }

    record Response(int status, String body) {}
}
