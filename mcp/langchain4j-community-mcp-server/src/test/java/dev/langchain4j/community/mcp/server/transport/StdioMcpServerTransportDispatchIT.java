package dev.langchain4j.community.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.support.InMemoryMcpTransport;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StdioMcpServerTransportDispatchIT {

    private static final int PIPE_BUFFER_SIZE = 10_240;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_execute_tools_outside_io_thread() throws Exception {
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
                StdioMcpServerTransport ignored = new StdioMcpServerTransport(
                        serverInputStream, serverOutputStream, new McpServer(List.of(new ThreadNameTool())));
                McpClient client = DefaultMcpClient.builder()
                        .transport(new InMemoryMcpTransport(clientInputStream, clientOutputStream))
                        .autoHealthCheck(false)
                        .toolExecutionTimeout(Duration.ofSeconds(10))
                        .build()) {
            ToolSpecification tool = toolByName(client.listTools(), "threadName");
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(tool.name())
                    .arguments(toJson(Map.of("value", "ping")))
                    .build();

            String threadName = client.executeTool(request).resultText();

            assertThat(threadName).isEqualTo("mcp-stdio-server-dispatcher");
            assertThat(threadName).isNotEqualTo("mcp-stdio-server");
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static ToolSpecification toolByName(List<ToolSpecification> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }

    private static String toJson(Map<String, Object> arguments) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(arguments);
    }

    static class ThreadNameTool {
        @Tool
        public String threadName(String value) {
            return Thread.currentThread().getName();
        }
    }
}
