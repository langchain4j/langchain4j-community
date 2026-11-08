package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.protocol.McpListToolsResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A tool method does not have to be declared in the class that is handed to the server: it can come from a superclass
 * or from an interface default method. Whatever {@code tools/list} advertises must also be callable.
 */
class McpServerInheritedToolsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_list_and_execute_a_tool_method_inherited_from_a_superclass() {
        McpServer server = new McpServer(List.of(new DerivedTool()));

        assertThat(listedToolNames(server)).contains("inherited");

        McpCallToolResult result = callTool(server, "inherited");

        assertThat(result.getResult().getIsError()).isNull();
        assertThat(resultText(result)).isEqualTo("from the superclass");
    }

    @Test
    void should_list_and_execute_a_tool_declared_as_an_interface_default_method() {
        McpServer server = new McpServer(List.of(new DefaultMethodTool()));

        assertThat(listedToolNames(server)).contains("defaultMethodTool");

        McpCallToolResult result = callTool(server, "defaultMethodTool");

        assertThat(result.getResult().getIsError()).isNull();
        assertThat(resultText(result)).isEqualTo("from the interface");
    }

    private static List<String> listedToolNames(McpServer server) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/list");

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpListToolsResult.class);

        McpListToolsResult listToolsResult = (McpListToolsResult) response;
        return listToolsResult.getResult().getTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
    }

    private static McpCallToolResult callTool(McpServer server, String name) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", OBJECT_MAPPER.createObjectNode());

        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "tools/call");
        request.set("params", params);

        McpJsonRpcMessage response = server.handle(request);
        assertThat(response).isInstanceOf(McpCallToolResult.class);
        return (McpCallToolResult) response;
    }

    private static String resultText(McpCallToolResult result) {
        return (String) result.getResult().getContent().get(0).get("text");
    }

    static class BaseTool {

        @Tool(name = "inherited")
        public String inherited() {
            return "from the superclass";
        }
    }

    static class DerivedTool extends BaseTool {}

    interface ToolsWithDefaultMethod {

        @Tool(name = "defaultMethodTool")
        default String defaultMethodTool() {
            return "from the interface";
        }
    }

    static class DefaultMethodTool implements ToolsWithDefaultMethod {}
}
