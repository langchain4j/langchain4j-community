package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.mcp.server.support.InMemoryMcpTransport;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerIntegrationTest {

    private static final int PIPE_BUFFER_SIZE = 10_240;

    @Test
    void should_execute_tool_over_piped_stdio() throws Exception {
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream)) {
            McpServer server = new McpServer(List.of(new Calculator()));
            try (StdioMcpServerTransport ignored =
                            new StdioMcpServerTransport(serverInputStream, serverOutputStream, server);
                    McpClient client = DefaultMcpClient.builder()
                            .transport(new InMemoryMcpTransport(clientInputStream, clientOutputStream))
                            .autoHealthCheck(false)
                            .build()) {
                List<ToolSpecification> tools = client.listTools();
                assertThat(tools).extracting(ToolSpecification::name).contains("add");

                ToolSpecification addTool = tools.stream()
                        .filter(tool -> "add".equals(tool.name()))
                        .findFirst()
                        .orElseThrow();
                Map<String, Object> argumentsMap = toolArgumentsFor(addTool, 1, 2);
                String arguments = new ObjectMapper().writeValueAsString(argumentsMap);
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .name("add")
                        .arguments(arguments)
                        .build();

                ToolExecutionResult result = client.executeTool(request);
                assertThat(result.resultText()).isEqualTo("3");
            }
        }
    }

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, long a, long b) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("a", a, "b", b);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("a") && keys.contains("b")) {
            return Map.of("a", a, "b", b);
        }
        if (keys.size() >= 2) {
            return Map.of(keys.get(0), a, keys.get(1), b);
        }
        return Map.of("a", a, "b", b);
    }

    static class Calculator {
        @Tool
        public long add(long a, long b) {
            return a + b;
        }
    }
}
