package dev.langchain4j.community.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.community.mcp.server.McpServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class McpHttpHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INITIALIZE =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}";

    private final McpHttpHandler handler = new McpHttpHandler(new McpServer(List.of(new Calculator())));

    @Test
    void should_answer_initialize_with_json() throws Exception {
        McpHttpHandler.Response response = post(handler, INITIALIZE, Map.of());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers()).containsEntry("Content-Type", "application/json");
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        assertThat(json.get("id").asLong()).isEqualTo(1);
        assertThat(json.at("/result/protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(json.at("/result/capabilities/tools").isObject()).isTrue();
    }

    @Test
    void should_call_tool() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"add\",\"arguments\":{\"a\":1,\"b\":2}}}";

        JsonNode json = OBJECT_MAPPER.readTree(post(handler, body, Map.of()).body());

        assertThat(json.get("id").asLong()).isEqualTo(7);
        assertThat(json.at("/result/content/0/text").asText()).isEqualTo("3");
    }

    @Test
    void should_preserve_string_id() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"abc-1\",\"method\":\"tools/list\"}";

        McpHttpHandler.Response response = post(handler, body, Map.of());

        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").asText()).isEqualTo("abc-1");
        assertThat(json.at("/result/tools/0/name").asText()).isEqualTo("add");
    }

    @Test
    void should_accept_notifications_without_body() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

        McpHttpHandler.Response response = post(handler, body, Map.of());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isNull();
    }

    @Test
    void should_accept_client_responses_without_body() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}";

        assertThat(post(handler, body, Map.of()).statusCode()).isEqualTo(202);
    }

    @Test
    void should_reject_get_and_delete() {
        for (String method : List.of("GET", "DELETE")) {
            McpHttpHandler.Response response = handler.handle(method, headers(Map.of()), null);

            assertThat(response.statusCode()).isEqualTo(405);
            assertThat(response.headers()).containsEntry("Allow", "POST");
        }
    }

    @Test
    void should_reject_unsupported_content_type() {
        McpHttpHandler.Response response =
                handler.handle("POST", headers(Map.of("Content-Type", "text/plain")), INITIALIZE);

        assertThat(response.statusCode()).isEqualTo(415);
    }

    @Test
    void should_reject_accept_without_json() {
        McpHttpHandler.Response response = post(handler, INITIALIZE, Map.of("Accept", "text/html"));

        assertThat(response.statusCode()).isEqualTo(406);
    }

    @Test
    void should_accept_json_and_event_stream() {
        McpHttpHandler.Response response =
                post(handler, INITIALIZE, Map.of("Accept", "application/json, text/event-stream"));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void should_answer_parse_error() throws Exception {
        McpHttpHandler.Response response = post(handler, "{not json", Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        assertThat(json.get("id").isNull()).isTrue();
        assertThat(json.at("/error/code").asInt()).isEqualTo(-32700);
    }

    @Test
    void should_reject_batches() throws Exception {
        McpHttpHandler.Response response = post(handler, "[" + INITIALIZE + "]", Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(OBJECT_MAPPER.readTree(response.body()).at("/error/code").asInt())
                .isEqualTo(-32600);
    }

    @Test
    void should_reject_unknown_origin() {
        McpHttpHandler.Response response = post(handler, INITIALIZE, Map.of("Origin", "https://evil.example"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void should_accept_allowed_origin() {
        McpHttpHandler allowing =
                new McpHttpHandler(new McpServer(List.of(new Calculator())), Set.of("https://app.example"));

        McpHttpHandler.Response response = post(allowing, INITIALIZE, Map.of("Origin", "https://app.example"));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static McpHttpHandler.Response post(McpHttpHandler handler, String body, Map<String, String> extra) {
        Map<String, String> headers = new HashMap<>(extra);
        headers.putIfAbsent("Content-Type", "application/json");
        return handler.handle("POST", headers(headers), body);
    }

    private static java.util.function.Function<String, String> headers(Map<String, String> headers) {
        return name -> headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    static class Calculator {

        @Tool
        public long add(@P(name = "a") long a, @P(name = "b") long b) {
            return a + b;
        }
    }
}
