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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

            assertThat(threadName).isEqualTo("mcp-stdio-server-dispatcher").isNotEqualTo("mcp-stdio-server");
        }
    }

    @Test
    void should_return_from_await_close_when_input_reaches_eof() throws Exception {
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
                StdioMcpServerTransport transport =
                        new StdioMcpServerTransport(serverInputStream, serverOutputStream, new McpServer(List.of()))) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread waiter = awaitCloseThread(transport, failure);

            clientOutputStream.close();
            waiter.join(5_000);

            assertThat(waiter.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
        }
    }

    @Test
    void should_return_from_await_close_when_transport_is_closed() throws Exception {
        try (CloseAwareInputStream serverInputStream = new CloseAwareInputStream();
                ByteArrayOutputStream serverOutputStream = new ByteArrayOutputStream()) {
            StdioMcpServerTransport transport =
                    new StdioMcpServerTransport(serverInputStream, serverOutputStream, new McpServer(List.of()));
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread waiter = awaitCloseThread(transport, failure);

            transport.close();
            waiter.join(5_000);

            assertThat(waiter.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static ToolSpecification toolByName(List<ToolSpecification> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.name()))
                .findFirst()
                .orElseThrow();
    }

    private static Thread awaitCloseThread(StdioMcpServerTransport transport, AtomicReference<Throwable> failure) {
        Thread waiter = new Thread(
                () -> {
                    try {
                        transport.awaitClose();
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                },
                "await-close-test");
        waiter.setDaemon(true);
        waiter.start();
        return waiter;
    }

    private static String toJson(Map<String, Object> arguments) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(arguments);
    }

    private static class CloseAwareInputStream extends InputStream {

        private boolean closed;

        @Override
        public synchronized int read() throws IOException {
            while (!closed) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for close", e);
                }
            }
            return -1;
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }
    }

    static class ThreadNameTool {
        @Tool
        public String threadName(String value) {
            return Thread.currentThread().getName();
        }
    }
}
