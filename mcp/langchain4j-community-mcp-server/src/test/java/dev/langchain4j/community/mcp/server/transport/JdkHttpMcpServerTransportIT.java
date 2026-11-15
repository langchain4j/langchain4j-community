package dev.langchain4j.community.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpMcpServerTransportIT {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private JdkHttpMcpServerTransport transport;

    @BeforeEach
    void start() {
        transport = JdkHttpMcpServerTransport.builder()
                .server(new McpServer(List.of(new Calculator())))
                .port(0)
                .maxRequestBodySize(1024)
                .build();
    }

    @AfterEach
    void stop() {
        transport.close();
    }

    @Test
    void should_serve_langchain4j_streamable_http_client() throws Exception {
        try (McpClient client = DefaultMcpClient.builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(transport.endpoint().toString())
                        .timeout(Duration.ofSeconds(10))
                        .build())
                .autoHealthCheck(false)
                .build()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).extracting(ToolSpecification::name).containsExactly("add");

            String result = client.executeTool(ToolExecutionRequest.builder()
                            .name("add")
                            .arguments("{\"a\":2,\"b\":3}")
                            .build())
                    .resultText();
            assertThat(result).isEqualTo("5");
        }
    }

    @Test
    void should_answer_404_outside_endpoint_path() throws Exception {
        HttpResponse<String> response = post(transport.endpoint().resolve("/other"), "{}");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void should_answer_413_for_oversized_body() throws Exception {
        HttpResponse<String> response = post(transport.endpoint(), "{\"padding\":\"" + "x".repeat(2048) + "\"}");

        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void should_answer_405_for_get() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(transport.endpoint()).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).hasValue("POST");
    }

    @Test
    void should_bind_to_loopback_by_default() {
        assertThat(transport.endpoint().getHost()).isEqualTo("127.0.0.1");
        assertThat(transport.port()).isPositive();
    }

    @Test
    void should_release_await_close_when_closed() throws Exception {
        CompletableFuture<Void> waiting = CompletableFuture.runAsync(() -> {
            try {
                transport.awaitClose();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        transport.close();

        waiting.get(5, TimeUnit.SECONDS);
        assertThat(waiting).isDone();
    }

    private HttpResponse<String> post(URI uri, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static class Calculator {

        @Tool
        public long add(@P(name = "a") long a, @P(name = "b") long b) {
            return a + b;
        }
    }
}
