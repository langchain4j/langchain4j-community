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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class McpServerConcurrencyIT {

    private static final int PIPE_BUFFER_SIZE = 10_240;
    private static final int REQUEST_COUNT = 50;
    private static final int CLIENT_POOL_SIZE = 8;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_handle_concurrent_requests_without_id_mixup() throws Exception {
        ExecutorService clientExecutor = Executors.newFixedThreadPool(CLIENT_POOL_SIZE);
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
                StdioMcpServerTransport ignored = new StdioMcpServerTransport(
                        serverInputStream, serverOutputStream, new McpServer(List.of(new SlowTool())));
                McpClient client = DefaultMcpClient.builder()
                        .transport(new InMemoryMcpTransport(clientInputStream, clientOutputStream))
                        .autoHealthCheck(false)
                        .toolExecutionTimeout(Duration.ofSeconds(10))
                        .build()) {
            ToolSpecification slowTool = toolByName(client.listTools(), "slowEcho");
            List<String> expected = new ArrayList<>();
            List<ToolExecutionRequest> requests = new ArrayList<>();

            for (int i = 0; i < REQUEST_COUNT; i++) {
                String value = "req-" + i;
                expected.add(value);
                Map<String, Object> argumentsMap = toolArgumentsFor(slowTool, value);
                requests.add(ToolExecutionRequest.builder()
                        .name(slowTool.name())
                        .arguments(toJson(argumentsMap))
                        .build());
            }

            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (ToolExecutionRequest request : requests) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> client.executeTool(request).resultText(), clientExecutor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

            for (int i = 0; i < futures.size(); i++) {
                assertThat(futures.get(i).get()).isEqualTo(expected.get(i));
            }
        } finally {
            clientExecutor.shutdownNow();
            assertThat(clientExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
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

    private static Map<String, Object> toolArgumentsFor(ToolSpecification tool, String value) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("value", value);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("value")) {
            return Map.of("value", value);
        }
        if (!keys.isEmpty()) {
            return Map.of(keys.get(0), value);
        }
        return Map.of("value", value);
    }

    static class SlowTool {
        @Tool
        public String slowEcho(String value) {
            long delayMillis = ThreadLocalRandom.current().nextLong(10, 50);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting");
            }
            return value;
        }
    }
}
