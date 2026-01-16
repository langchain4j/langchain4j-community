package dev.langchain4j.community.tool.webscraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebScraperClientTest {

    private HttpServer server;
    private WebScraperClient client;

    @BeforeEach
    void setUp() {
        client = new WebScraperClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void should_convert_html_to_markdown() {
        String html = "<html><body>"
                + "<nav>Navigation</nav>"
                + "<h1>Main Title</h1>"
                + "<h6>Footer Title</h6>"
                + "<p>Intro <b>Bold</b> and <i>Italic</i> with <a href=\"/path\">Link</a>.<br>Line two</p>"
                + "<p><a href=\"/blank\"></a> <a>TextOnly</a></p>"
                + "<ol><li>First</li><li>Second</li></ol>"
                + "<ul>"
                + "<li>Item 1<ul><li>Nested 1</li></ul></li>"
                + "<li>Item 2</li>"
                + "</ul>"
                + "<style>.hidden{}</style>"
                + "<footer>Copyright</footer>"
                + "<iframe src=\"http://example.com\"></iframe>"
                + "<script>console.log('x')</script>"
                + "</body></html>";

        String result = client.htmlToMarkdown(html, URI.create("http://localhost:8080/base"));

        assertThat(result)
                .contains("# Main Title")
                .contains("###### Footer Title")
                .contains("- Item 1")
                .contains("  - Nested 1")
                .contains("- Item 2")
                .contains("- First")
                .contains("- Second")
                .contains("[Link](http://localhost:8080/path)")
                .contains("TextOnly")
                .contains("http://localhost:8080/blank")
                .contains("Bold")
                .contains("Italic")
                .contains("Line two")
                .doesNotContain("Navigation")
                .doesNotContain(".hidden{}")
                .doesNotContain("Copyright")
                .doesNotContain("example.com")
                .doesNotContain("console.log");
    }

    @Test
    void should_send_user_agent_header() throws Exception {
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        String url = startServer(exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });

        String body = client.fetchHtml(url);

        assertThat(body).isEqualTo("ok");
        assertThat(userAgent.get()).isEqualTo("langchain4j-community-web-scraper");
        assertThat(acceptHeader.get()).isEqualTo("text/html,application/xhtml+xml");
    }

    @Test
    void should_handle_relative_link_without_base() {
        String html = "<html><body><a href=\"/relative\">Link</a></body></html>";

        String result = client.htmlToMarkdown(html);

        assertThat(result).contains("[Link](/relative)");
    }

    @Test
    void should_normalize_whitespace_and_preserve_unicode() {
        String html = "<html><body><p>Hello   \n  World \u00A9 \u4F60\u597D</p></body></html>";

        String result = client.htmlToMarkdown(html);

        assertThat(result).contains("Hello World \u00A9 \u4F60\u597D");
    }

    @Test
    void should_include_pre_and_blockquote_text() {
        String html = "<html><body><pre>Line 1\nLine 2</pre><blockquote>Quoted</blockquote></body></html>";

        String result = client.htmlToMarkdown(html);

        assertThat(result).contains("Line 1 Line 2").contains("Quoted");
    }

    @Test
    void should_return_empty_for_empty_html() {
        String result = client.htmlToMarkdown("");

        assertThat(result).isEmpty();
    }

    @Test
    void should_fetch_plain_text_content_type() throws Exception {
        String url = startServer(200, "plain-text", "text/plain; charset=UTF-8");

        String body = client.fetchHtml(url);

        assertThat(body).isEqualTo("plain-text");
    }

    @Test
    void should_fetch_large_response() throws Exception {
        String body = "x".repeat(200_000);
        String url = startServer(200, body);

        String result = client.fetchHtml(url);

        assertThat(result).hasSameSizeAs(body);
    }

    @Test
    void should_throw_on_404() throws Exception {
        String url = startServer(404, "<html><body>Not Found</body></html>");

        assertThatThrownBy(() -> client.fetchHtml(url))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("404");
    }

    @Test
    void should_throw_on_500() throws Exception {
        String url = startServer(500, "<html><body>Server Error</body></html>");

        assertThatThrownBy(() -> client.fetchHtml(url))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("500");
    }

    private String startServer(int status, String body) throws IOException {
        return startServer(status, body, "text/html; charset=UTF-8");
    }

    private String startServer(int status, String body, String contentType) throws IOException {
        return startServer(exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
    }

    private String startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/test", handler);
        server.start();
        int port = server.getAddress().getPort();
        return "http://localhost:" + port + "/test";
    }
}
