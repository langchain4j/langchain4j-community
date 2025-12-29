package dev.langchain4j.community.tool.webscraper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebScraperToolTest {

    private HttpServer server;
    private WebScraperTool tool;

    @BeforeEach
    void setUp() {
        tool = new WebScraperTool();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void should_scrape_url_successfully() throws IOException {
        String html = "<html><body><h1>Title</h1><p>Content</p></body></html>";
        String url = startServer(200, html);

        String result = tool.scrapeUrl(url);

        assertThat(result).contains("Title");
        assertThat(result).doesNotStartWith("Error:");
    }

    @Test
    void should_return_error_string_on_failure() throws IOException {
        String url = startServer(500, "<html><body>Server Error</body></html>");

        String result = tool.scrapeUrl(url);

        assertThat(result).startsWith("Error:");
        assertThat(result).contains("500");
    }

    @Test
    void should_restore_interrupt_status() {
        WebScraperClient interruptingClient = new WebScraperClient(new InterruptingHttpClient());
        WebScraperTool interruptingTool = new WebScraperTool(interruptingClient);

        try {
            String result = interruptingTool.scrapeUrl("http://localhost/test");

            assertThat(result).isEqualTo("Error: Scraping was interrupted.");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void should_return_generic_error_for_blank_exception_message() {
        WebScraperClient failingClient = new WebScraperClient(new FailingHttpClient());
        WebScraperTool failingTool = new WebScraperTool(failingClient);

        String result = failingTool.scrapeUrl("http://localhost/test");

        assertThat(result).isEqualTo("Error: Failed to fetch URL.");
    }

    @Test
    void should_return_error_for_malformed_url() {
        String result = tool.scrapeUrl("htp://invalid-url");

        assertThat(result).startsWith("Error:");
    }

    @Test
    void should_return_error_for_connection_refused() throws IOException {
        int port = findUnusedPort();
        String url = "http://localhost:" + port + "/missing";

        String result = tool.scrapeUrl(url);

        assertThat(result).startsWith("Error:");
    }

    @Test
    void should_return_error_for_not_found() throws IOException {
        String url = startServer(404, "<html><body>Not Found</body></html>");

        String result = tool.scrapeUrl(url);

        assertThat(result).startsWith("Error:");
        assertThat(result).contains("404");
    }

    private String startServer(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/test", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        return "http://localhost:" + port + "/test";
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private abstract static class BaseHttpClient implements HttpClient {
        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException("SSE not supported");
        }
    }

    private static final class InterruptingHttpClient extends BaseHttpClient {
        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new RuntimeException(new InterruptedException("interrupted"));
        }
    }

    private static final class FailingHttpClient extends BaseHttpClient {
        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new RuntimeException(new IOException());
        }
    }
}
