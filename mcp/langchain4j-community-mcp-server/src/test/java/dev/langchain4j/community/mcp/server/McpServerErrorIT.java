package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.mcp.server.support.InMemoryMcpTransport;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class McpServerErrorIT {

    private static final int PIPE_BUFFER_SIZE = 10_240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long RAW_REQUEST_ID = 10_000L;

    @Test
    void should_handle_errors_without_exiting_transport() throws Exception {
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
                StdioMcpServerTransport ignored = new StdioMcpServerTransport(
                        serverInputStream, serverOutputStream, new McpServer(List.of(new ErrorTool())))) {
            InMemoryMcpTransport transport = new InMemoryMcpTransport(clientInputStream, clientOutputStream);
            try (McpClient client = DefaultMcpClient.builder()
                    .transport(transport)
                    .autoHealthCheck(false)
                    .toolExecutionTimeout(Duration.ofSeconds(5))
                    .build()) {
                List<ToolSpecification> tools = client.listTools();
                ToolSpecification failTool = toolByName(tools, "fail");
                ToolSpecification addTool = toolByName(tools, "add");

                ToolExecutionRequest failRequest = ToolExecutionRequest.builder()
                        .name("fail")
                        .arguments(toJson(toolArgumentsFor(failTool, "boom")))
                        .build();

                assertThatThrownBy(() -> client.executeTool(failRequest))
                        .isInstanceOf(ToolExecutionException.class)
                        .hasMessageContaining("Invalid input");

                JsonNode unknownToolResponse =
                        executeRawCall(transport, "non_existent_tool", OBJECT_MAPPER.createObjectNode());
                assertThat(unknownToolResponse.has("error")).isFalse();
                JsonNode unknownToolResult = unknownToolResponse.get("result");
                assertThat(unknownToolResult.get("isError").asBoolean()).isTrue();
                assertThat(unknownToolResult.get("content").get(0).get("text").asText())
                        .contains("Unknown tool");

                ToolExecutionRequest missingArgsRequest = ToolExecutionRequest.builder()
                        .name("add")
                        .arguments(toJson(toolArgumentsFor(addTool, 1L)))
                        .build();

                assertThatThrownBy(() -> client.executeTool(missingArgsRequest))
                        .isInstanceOf(ToolExecutionException.class)
                        .hasMessageContaining("Missing args");

                ToolExecutionRequest okRequest = ToolExecutionRequest.builder()
                        .name("add")
                        .arguments(toJson(toolArgumentsFor(addTool, 1L, 2L)))
                        .build();
                ToolExecutionResult okResult = client.executeTool(okRequest);
                assertThat(okResult.resultText()).isEqualTo("3");
            }
        }
    }

    private static ToolSpecification toolByName(List<ToolSpecification> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }

    private static String toJson(Map<String, Object> arguments) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(arguments);
    }

    @SuppressWarnings("SameParameterValue")
    private static JsonNode executeRawCall(InMemoryMcpTransport transport, String toolName, ObjectNode arguments)
            throws Exception {
        McpCallToolRequest request = new McpCallToolRequest(RAW_REQUEST_ID, toolName, arguments);
        return transport.executeOperationWithResponse(request).get(5, TimeUnit.SECONDS);
    }

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, Object... values) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of();
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        Map<String, Object> args = new LinkedHashMap<>();
        int count = Math.min(values.length, keys.size());
        for (int i = 0; i < count; i++) {
            args.put(keys.get(i), values[i]);
        }
        return args;
    }

    static class ErrorTool {
        @Tool
        public String fail(String input) {
            throw new IllegalArgumentException("Invalid input");
        }

        @Tool
        public long add(Long a, Long b) {
            if (a == null || b == null) {
                throw new IllegalArgumentException("Missing args");
            }
            return a + b;
        }
    }
}
