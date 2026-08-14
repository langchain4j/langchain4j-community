package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class McpServerLargePayloadIT {

    private static final int PIPE_BUFFER_SIZE = 1_024;
    private static final int PAYLOAD_SIZE_BYTES = 1024 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_handle_large_payload_without_deadlock() throws Exception {
        ExecutorService clientExecutor = Executors.newSingleThreadExecutor();
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
                StdioMcpServerTransport ignored = new StdioMcpServerTransport(
                        serverInputStream, serverOutputStream, new McpServer(List.of(new EchoTool())));
                McpClient client = DefaultMcpClient.builder()
                        .transport(new InMemoryMcpTransport(clientInputStream, clientOutputStream))
                        .autoHealthCheck(false)
                        .toolExecutionTimeout(Duration.ofSeconds(30))
                        .build()) {
            ToolSpecification echoTool = toolByName(client.listTools(), "echo");
            String payload = generatePayload(PAYLOAD_SIZE_BYTES);
            Map<String, Object> arguments = toolArgumentsFor(echoTool, payload);

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(echoTool.name())
                    .arguments(toJson(arguments))
                    .build();

            CompletableFuture<ToolExecutionResult> future =
                    CompletableFuture.supplyAsync(() -> client.executeTool(request), clientExecutor);
            ToolExecutionResult result = future.get(60, TimeUnit.SECONDS);

            assertThat(result.resultText()).isEqualTo(payload);
        } finally {
            clientExecutor.shutdownNow();
            assertThat(clientExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
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

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, String input) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("input", input);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("input")) {
            return Map.of("input", input);
        }
        if (!keys.isEmpty()) {
            return Map.of(keys.get(0), input);
        }
        return Map.of("input", input);
    }

    private static String generatePayload(int size) {
        Random random = new Random(42);
        StringBuilder builder = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        return builder.toString();
    }

    static class EchoTool {
        @Tool
        public String echo(String input) {
            return input;
        }
    }
}
