package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerResourcesPromptsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_not_advertise_unused_capabilities() {
        McpServer server = new McpServer(List.of(new Weather()));

        JsonNode tree = toTree(server.handle(initializeRequest(1)));

        assertThat(tree.get("result").get("capabilities").get("tools")).isNotNull();
        assertThat(tree.get("result").get("capabilities").has("resources")).isFalse();
        assertThat(tree.get("result").get("capabilities").has("prompts")).isFalse();
    }

    @Test
    void should_advertise_registered_capabilities() {
        McpServer server = McpServer.builder()
                .tools(List.of(new Weather()))
                .resources(List.of(new ConfigResource()))
                .prompts(List.of(new GreetingPrompts()))
                .build();

        JsonNode tree = toTree(server.handle(initializeRequest(1)));
        JsonNode capabilities = tree.get("result").get("capabilities");

        assertThat(capabilities.get("tools")).isNotNull();
        assertThat(capabilities.get("resources")).isNotNull();
        assertThat(capabilities.get("prompts")).isNotNull();
    }

    @Test
    void should_list_and_read_resources() {
        McpServer server = McpServer.builder()
                .resources(List.of(new ConfigResource(), new DocsResource()))
                .build();

        JsonNode list = toTree(server.handle(request(1, "resources/list", null)));
        JsonNode resources = list.get("result").get("resources");

        assertThat(resources).hasSize(2);
        assertThat(resources.get(0).get("uri").asText()).isEqualTo("config://app/settings");
        assertThat(resources.get(0).get("name").asText()).isEqualTo("Application Settings");
        assertThat(resources.get(0).get("mimeType").asText()).isEqualTo("application/json");
        // nextCursor is optional: with no pagination the field must be absent, not null.
        assertThat(list.get("result").has("nextCursor")).isFalse();

        JsonNode read = toTree(server.handle(request(2, "resources/read", Map.of("uri", "config://app/settings"))));

        assertThat(read.get("result").get("contents")).hasSize(1);
        assertThat(read.get("result").get("contents").get(0).get("uri").asText())
                .isEqualTo("config://app/settings");
        assertThat(read.get("result").get("contents").get(0).get("mimeType").asText())
                .isEqualTo("application/json");
        assertThat(read.get("result").get("contents").get(0).get("text").asText())
                .isEqualTo("{\"mode\":\"quiet\",\"retries\":3}");
    }

    @Test
    void should_return_error_for_unknown_resource() {
        McpServer server =
                McpServer.builder().resources(List.of(new ConfigResource())).build();

        JsonNode tree = toTree(server.handle(request(1, "resources/read", Map.of("uri", "nope://missing"))));

        assertThat(tree.get("error").get("code").asInt()).isEqualTo(-32002);
        assertThat(tree.get("error").get("message").asText()).contains("Resource not found");
    }

    @Test
    void should_list_prompts_with_argument_schemas() {
        McpServer server =
                McpServer.builder().prompts(List.of(new GreetingPrompts())).build();

        JsonNode tree = toTree(server.handle(request(1, "prompts/list", null)));
        JsonNode prompts = tree.get("result").get("prompts");

        assertThat(prompts).hasSize(2);
        JsonNode greet = findByName(prompts, "greet");
        assertThat(greet).isNotNull();
        assertThat(greet.get("arguments")).hasSize(2);
        assertThat(greet.get("arguments").get(0).get("name").asText()).isEqualTo("name");
        assertThat(greet.get("arguments").get(0).get("required").asBoolean()).isTrue();
        assertThat(findByName(prompts, "farewell")).isNotNull();
        assertThat(tree.get("result").has("nextCursor")).isFalse();
    }

    @Test
    void should_get_prompt_with_arguments() {
        McpServer server =
                McpServer.builder().prompts(List.of(new GreetingPrompts())).build();

        JsonNode tree = toTree(server.handle(request(
                1, "prompts/get", Map.of("name", "greet", "arguments", Map.of("name", "Ada", "formal", "true")))));

        JsonNode messages = tree.get("result").get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").get("type").asText()).isEqualTo("text");
        assertThat(messages.get(0).get("content").get("text").asText()).isEqualTo("Hello, formal Ada!");
        assertThat(tree.get("result").get("description").asText()).isEqualTo("Greets someone");
    }

    @Test
    void should_return_error_when_required_prompt_argument_is_missing() {
        McpServer server =
                McpServer.builder().prompts(List.of(new GreetingPrompts())).build();

        JsonNode tree = toTree(server.handle(
                request(1, "prompts/get", Map.of("name", "greet", "arguments", Map.of("formal", "true")))));

        assertThat(tree.get("error").get("code").asInt()).isEqualTo(-32602);
        assertThat(tree.get("error").get("message").asText()).contains("name");
    }

    @Test
    void should_return_error_for_unknown_prompt() {
        McpServer server =
                McpServer.builder().prompts(List.of(new GreetingPrompts())).build();

        JsonNode tree = toTree(server.handle(request(1, "prompts/get", Map.of("name", "nope"))));

        assertThat(tree.get("error").get("code").asInt()).isEqualTo(-32602);
        assertThat(tree.get("error").get("message").asText()).contains("Unknown prompt");
    }

    @Test
    void should_still_return_method_not_found_for_other_methods() {
        McpServer server = McpServer.builder()
                .resources(List.of(new ConfigResource()))
                .prompts(List.of(new GreetingPrompts()))
                .build();

        McpJsonRpcMessage response = server.handle(request(1, "sampling/createMessage", null));

        assertThat(response).isInstanceOf(McpErrorResponse.class);
        assertThat(((McpErrorResponse) response).getError().getCode()).isEqualTo(-32601);
    }

    @Test
    void should_reject_duplicated_resource_uri() {
        assertThatThrownBy(() -> McpServer.builder()
                        .resources(List.of(new ConfigResource(), new ConfigResource()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicated resource uri");
    }

    @Test
    void should_reject_duplicated_prompt_name() {
        assertThatThrownBy(() -> McpServer.builder()
                        .prompts(List.of(new GreetingPrompts(), new GreetingPrompts()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicated prompt name");
    }

    @Test
    void should_sort_resources_by_uri_and_prompts_by_name() {
        // Registered in reverse declaration order to prove the advertised order is sorted, not
        // whatever getDeclaredMethods() happens to return.
        McpServer server = McpServer.builder()
                .resources(List.of(new DocsResource(), new ConfigResource()))
                .prompts(List.of(new GreetingPrompts()))
                .build();

        JsonNode resources = toTree(server.handle(request(1, "resources/list", null)))
                .get("result")
                .get("resources");
        assertThat(resources.get(0).get("uri").asText()).isEqualTo("config://app/settings");
        assertThat(resources.get(1).get("uri").asText()).isEqualTo("docs://readme");

        JsonNode prompts = toTree(server.handle(request(2, "prompts/list", null)))
                .get("result")
                .get("prompts");
        assertThat(prompts.get(0).get("name").asText()).isEqualTo("farewell");
        assertThat(prompts.get(1).get("name").asText()).isEqualTo("greet");
    }

    @Test
    void should_return_internal_error_and_not_leak_message_when_resource_throws() {
        McpServer server =
                McpServer.builder().resources(List.of(new ThrowingResource())).build();

        JsonNode tree = toTree(server.handle(request(1, "resources/read", Map.of("uri", "boom://resource"))));

        assertThat(tree.get("error").get("code").asInt()).isEqualTo(-32603);
        assertThat(tree.get("error").get("message").asText()).doesNotContain("secret-resource-detail");
    }

    @Test
    void should_return_internal_error_and_not_leak_message_when_prompt_throws() {
        McpServer server =
                McpServer.builder().prompts(List.of(new ThrowingPrompt())).build();

        JsonNode tree = toTree(server.handle(request(1, "prompts/get", Map.of("name", "boom"))));

        assertThat(tree.get("error").get("code").asInt()).isEqualTo(-32603);
        assertThat(tree.get("error").get("message").asText()).doesNotContain("secret-prompt-detail");
    }

    @Test
    void should_reject_duplicated_prompt_argument_name() {
        assertThatThrownBy(() -> McpServer.builder()
                        .prompts(List.of(new DuplicateArgumentPrompt()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicated prompt argument name");
    }

    @Test
    void should_reject_non_public_resource_method() {
        assertThatThrownBy(() -> McpServer.builder()
                        .resources(List.of(new NonPublicResource()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be public");
    }

    @Test
    void should_reject_non_string_resource_return_type() {
        assertThatThrownBy(() -> McpServer.builder()
                        .resources(List.of(new NonStringResource()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return String");
    }

    @Test
    void should_reject_non_prompt_message_list_return_type() {
        assertThatThrownBy(() -> McpServer.builder()
                        .prompts(List.of(new NonPromptMessageListPrompt()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List<PromptMessage>");
    }

    @Test
    void should_allow_empty_prompt_text() {
        PromptMessage message = PromptMessage.user("");

        assertThat(message.getContent().get("text")).isEqualTo("");
    }

    private static JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        return null;
    }

    private static JsonNode toTree(McpJsonRpcMessage response) {
        return OBJECT_MAPPER.valueToTree(response);
    }

    private static ObjectNode initializeRequest(int id) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", "initialize");
        ObjectNode params = request.putObject("params");
        params.put("protocolVersion", "2025-06-18");
        return request;
    }

    private static ObjectNode request(int id, String method, Map<String, Object> params) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", OBJECT_MAPPER.valueToTree(params));
        }
        return request;
    }

    static class Weather {

        @Tool
        public String forecast(String city) {
            return "Sunny in " + city;
        }
    }

    static class ConfigResource {

        @Resource(
                uri = "config://app/settings",
                name = "Application Settings",
                description = "Current application configuration",
                mimeType = "application/json")
        public String getSettings() {
            return "{\"mode\":\"quiet\",\"retries\":3}";
        }
    }

    static class DocsResource {

        @Resource(uri = "docs://readme", name = "README")
        public String getReadme() {
            return "# Docs\nRun the app.";
        }
    }

    static class GreetingPrompts {

        @Prompt(name = "greet", description = "Greets someone")
        public List<PromptMessage> greet(
                @PromptArg(name = "name", description = "Who to greet", required = true) String name,
                @PromptArg(name = "formal", description = "Use formal tone") String formal) {
            String text = formal != null ? "Hello, formal " + name + "!" : "Hello, " + name + "!";
            return List.of(PromptMessage.user(text));
        }

        @Prompt(name = "farewell")
        public List<PromptMessage> farewell(@PromptArg(name = "name", required = true) String name) {
            return List.of(PromptMessage.user("Goodbye, " + name + "!"), PromptMessage.assistant("See you."));
        }
    }

    static class ThrowingResource {

        @Resource(uri = "boom://resource", name = "Boom")
        public String read() {
            throw new IllegalStateException("secret-resource-detail");
        }
    }

    static class ThrowingPrompt {

        @Prompt(name = "boom")
        public List<PromptMessage> boom() {
            throw new IllegalStateException("secret-prompt-detail");
        }
    }

    static class DuplicateArgumentPrompt {

        @Prompt(name = "dup")
        public List<PromptMessage> dup(@PromptArg(name = "x") String first, @PromptArg(name = "x") String second) {
            return List.of(PromptMessage.user(first + second));
        }
    }

    static class NonPublicResource {

        @Resource(uri = "private://resource", name = "Private")
        String read() {
            return "nope";
        }
    }

    static class NonStringResource {

        @Resource(uri = "number://resource", name = "Number")
        public Integer read() {
            return 42;
        }
    }

    static class NonPromptMessageListPrompt {

        @Prompt(name = "strings")
        public List<String> strings() {
            return List.of("a", "b");
        }
    }
}
