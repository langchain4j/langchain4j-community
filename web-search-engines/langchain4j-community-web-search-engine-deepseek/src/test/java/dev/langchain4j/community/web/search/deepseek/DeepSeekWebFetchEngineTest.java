package dev.langchain4j.community.web.search.deepseek;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeepSeekWebFetchEngineTest {

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"title\":\"Fetched page\",\"content\":\"# Markdown content\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));

            byte[] bytes = responseBody.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private DeepSeekWebFetchEngine.Builder engineBuilder() {
        return DeepSeekWebFetchEngine.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort() + "/v1/tools/")
                .apiKey("test-key");
    }

    @Test
    void should_require_api_key() {
        assertThatThrownBy(() -> DeepSeekWebFetchEngine.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> DeepSeekWebFetchEngine.withApiKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_fetch_page_content() {
        DeepSeekFetchResult result = engineBuilder().build().fetch("https://example.com/page");

        assertThat(requestPath.get()).isEqualTo("/v1/tools/fetch");
        assertThat(requestAuthorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get()).isEqualTo("{\"url\":\"https://example.com/page\"}");
        assertThat(result.title()).isEqualTo("Fetched page");
        assertThat(result.content()).isEqualTo("# Markdown content");
    }

    @Test
    void should_require_url() {
        assertThatThrownBy(() -> engineBuilder().build().fetch(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void should_propagate_http_errors() {
        responseStatus = 401;
        responseBody = "{\"error\":\"Unauthorized\"}";

        assertThatThrownBy(() -> engineBuilder().build().fetch("https://example.com/page"))
                .isInstanceOf(HttpException.class);
    }

    @Test
    void should_handle_empty_fetch_response() {
        responseBody = "{}";

        DeepSeekFetchResult result = engineBuilder().build().fetch("https://example.com/page");

        assertThat(result.title()).isNull();
        assertThat(result.content()).isNull();
    }
}
