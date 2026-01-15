package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpServerEdgeCasesTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_return_null_for_null_message() {
        McpServer server = new McpServer(List.of());

        McpJsonRpcMessage response = server.handle(null);

        assertThat(response).isNull();
    }

    @Test
    void should_return_invalid_request_error_for_non_textual_method() {
        McpServer server = new McpServer(List.of());

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", 123);

        McpJsonRpcMessage response = server.handle(request);

        assertThat(response).isInstanceOf(McpErrorResponse.class);
        McpErrorResponse errorResponse = (McpErrorResponse) response;
        assertThat(errorResponse.getError().getCode()).isEqualTo(-32600);
        assertThat(errorResponse.getError().getMessage()).contains("Invalid Request");
    }

    @Test
    void should_return_invalid_request_error_for_blank_method() {
        McpServer server = new McpServer(List.of());

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "   ");

        McpJsonRpcMessage response = server.handle(request);

        assertThat(response).isInstanceOf(McpErrorResponse.class);
        McpErrorResponse errorResponse = (McpErrorResponse) response;
        assertThat(errorResponse.getError().getCode()).isEqualTo(-32600);
        assertThat(errorResponse.getError().getMessage()).contains("Invalid Request");
    }

    @Test
    void should_return_exception_class_name_when_tool_throws_blank_message() {
        McpServer server = new McpServer(List.of(new BlankMessageTool()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "blankMessage");
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
        assertThat(callToolResult.getResult().getContent().get(0).getText()).contains("ToolExecutionException");
    }

    @Test
    void should_return_error_when_arguments_field_is_missing() {
        McpServer server = new McpServer(List.of(new RequiredInputTool()));

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "echo");

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
        assertThat(callToolResult.getResult().getContent().get(0).getText()).contains("Missing input");
    }

    static class BlankMessageTool {
        @Tool
        public String blankMessage() {
            throw new IllegalStateException("");
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
}
