package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.protocol.McpListToolsResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_return_null_for_missing_method() {
        McpServer server = new McpServer(List.of(new Calculator()));

        McpJsonRpcMessage response = server.handle(OBJECT_MAPPER.createObjectNode());
        assertThat(response).isNull();
    }

    @Test
    void should_return_null_for_missing_id() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "tools/list");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isNull();
    }

    @Test
    void should_return_null_for_non_numeric_id() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "string-id");
        request.put("method", "tools/list");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isNull();
    }

    @Test
    void should_return_error_for_unknown_method() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "does/not/exist");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpErrorResponse.class);

        McpErrorResponse errorResponse = (McpErrorResponse) response;
        assertThat(errorResponse.getError().getCode()).isEqualTo(-32601);
        assertThat(errorResponse.getError().getMessage()).contains("Method not found");
    }

    @Test
    void should_return_initialize_response_with_default_protocol_version() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpInitializeResult.class);

        McpInitializeResult initializeResult = (McpInitializeResult) response;
        assertThat(initializeResult.getResult().getProtocolVersion()).isEqualTo("2025-06-18");
        assertThat(initializeResult.getResult().getCapabilities().getTools()).isNotNull();
        assertThat(initializeResult.getResult().getServerInfo().getName()).isEqualTo("LangChain4j");
        assertThat(initializeResult.getResult().getServerInfo().getVersion()).isNotBlank();
    }

    @Test
    void should_return_initialize_response_with_requested_protocol_version() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("protocolVersion", "2025-06-18");

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpInitializeResult.class);

        McpInitializeResult initializeResult = (McpInitializeResult) response;
        assertThat(initializeResult.getResult().getProtocolVersion()).isEqualTo("2025-06-18");
    }

    @Test
    void should_list_tools() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/list");
        request.set("params", OBJECT_MAPPER.createObjectNode());

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpListToolsResult.class);

        McpListToolsResult listToolsResult = (McpListToolsResult) response;
        assertThat(listToolsResult.getResult().getTools())
                .extracting(tool -> String.valueOf(tool.get("name")))
                .contains("add");
    }

    @Test
    void should_execute_tool_call() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("a", 1);
        arguments.put("b", 2);
        arguments.put("arg0", 1);
        arguments.put("arg1", 2);

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "add");
        params.set("arguments", arguments);

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);

        McpCallToolResult callToolResult = (McpCallToolResult) response;
        assertThat(callToolResult.getResult().getIsError()).isNull();
        assertThat(callToolResult.getResult().getContent()).hasSize(1);
        assertThat(callToolResult.getResult().getContent().get(0).getText()).isEqualTo("3");
    }

    @Test
    void should_return_error_when_tool_name_is_missing() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.set("arguments", OBJECT_MAPPER.createObjectNode());

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpErrorResponse.class);

        McpErrorResponse errorResponse = (McpErrorResponse) response;
        assertThat(errorResponse.getError().getCode()).isEqualTo(-32602);
        assertThat(errorResponse.getError().getMessage()).contains("Missing tool name");
    }

    @Test
    void should_return_error_when_tool_does_not_exist() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "doesNotExist");
        params.set("arguments", OBJECT_MAPPER.createObjectNode());

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);

        McpCallToolResult callToolResult = (McpCallToolResult) response;
        assertThat(callToolResult.getResult().getIsError()).isTrue();
        assertThat(callToolResult.getResult().getContent()).hasSize(1);
        assertThat(callToolResult.getResult().getContent().get(0).getText()).contains("Unknown tool");
    }

    @Test
    void should_return_error_when_tool_execution_fails() {
        McpServer server = new McpServer(List.of(new ErrorTool()));

        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("input", "boom");
        arguments.put("arg0", "boom");

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "fail");
        params.set("arguments", arguments);

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);

        McpCallToolResult callToolResult = (McpCallToolResult) response;
        assertThat(callToolResult.getResult().getIsError()).isTrue();
        assertThat(callToolResult.getResult().getContent().get(0).getText()).contains("Invalid input");
    }

    @Test
    void should_not_throw_when_arguments_are_not_an_object() {
        McpServer server = new McpServer(List.of(new RequiredInputTool()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "echo");
        params.put("arguments", "not-an-object");

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);

        McpCallToolResult callToolResult = (McpCallToolResult) response;
        assertThat(callToolResult.getResult().getIsError()).isTrue();
        assertThat(callToolResult.getResult().getContent().get(0).getText()).contains("Missing input");
    }

    @Test
    void should_fail_fast_on_duplicated_tool_name() {
        assertThatThrownBy(() -> new McpServer(List.of(new DuplicateTool())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool names must be unique");
    }

    static class Calculator {
        @Tool
        public long add(long a, long b) {
            return a + b;
        }
    }

    static class ErrorTool {
        @Tool
        public String fail(String input) {
            throw new IllegalArgumentException("Invalid input");
        }
    }

    static class RequiredInputTool {
        @Tool
        public String echo(String input) {
            if (input == null) {
                throw new IllegalArgumentException("Missing input");
            }
            return input;
        }
    }

    static class DuplicateTool {
        @Tool(name = "duplicate")
        public String first() {
            return "first";
        }

        @Tool(name = "duplicate")
        public String second() {
            return "second";
        }
    }

    @SuppressWarnings("unused")
    static class ToolWithMeta {
        @Tool(metadata = "{\"key\": \"value\"}")
        public String echo(String input) {
            return input;
        }
    }

    @Test
    void should_not_crash_on_invalid_params_shape() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");
        request.put("params", "not-an-object");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpInitializeResult.class);
    }

    @Test
    void should_not_crash_on_non_object_cursor_params() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/list");
        request.put("params", "not-an-object");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpListToolsResult.class);
    }

    @Test
    void should_not_crash_when_params_is_missing() {
        McpServer server = new McpServer(List.of(new Calculator()));

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpErrorResponse.class);
    }

    @Test
    void should_execute_tool_call_with_null_arguments_field() {
        McpServer server = new McpServer(List.of(new EchoTool()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "echo");
        params.putNull("arguments");

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);

        McpCallToolResult callToolResult = (McpCallToolResult) response;
        assertThat(callToolResult.getResult().getIsError()).isNull();
        assertThat(callToolResult.getResult().getContent().get(0).getText()).isEqualTo("Success");
    }

    static class EchoTool {
        @Tool
        public void echo() {}
    }

    @Test
    void should_allow_custom_tool_execution_result_to_be_serialized() {
        McpServer server = new McpServer(List.of(new ComplexReturnTool()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "complex");
        params.set("arguments", OBJECT_MAPPER.createObjectNode());

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);

        McpCallToolResult callToolResult = (McpCallToolResult) response;
        assertThat(callToolResult.getResult().getIsError()).isNull();
        assertThat(callToolResult.getResult().getContent().get(0).getText()).contains("value");
    }

    static class ComplexReturnTool {
        @Tool
        public Map<String, Object> complex() {
            return Map.of("key", "value");
        }
    }
}
