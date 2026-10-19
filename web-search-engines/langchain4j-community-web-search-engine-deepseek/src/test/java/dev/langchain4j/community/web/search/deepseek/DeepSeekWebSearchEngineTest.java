package dev.langchain4j.community.web.search.deepseek;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeepSeekWebSearchEngineTest {

    private static final String SEARCH_RESPONSE = """
        {
          "results": [
            {
              "rerank_score": 2.4,
              "title": "DeepSeek V4 model released",
              "url": "https://example.com/deepseek-v4",
              "snippet": "DeepSeek V4 is now available, with tool calling and long-context support.",
              "summary": "DeepSeek has released the V4 model. The release adds improved tool calling and long-context support for agent workflows.",
              "content": "DeepSeek V4 is now generally available...",
              "site_name": "Example News",
              "site_icon": "https://example.com/favicon.ico",
              "date": "2026-06-01T00:00:00+00:00",
              "authority": 3
            }
          ]
        }
    """;

    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = SEARCH_RESPONSE;

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

    private DeepSeekWebSearchEngine.Builder engineBuilder() {
        return DeepSeekWebSearchEngine.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort() + "/v1/tools/")
                .apiKey("test-key");
    }

    @Test
    void should_require_api_key() {
        assertThatThrownBy(() -> DeepSeekWebSearchEngine.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> DeepSeekWebSearchEngine.withApiKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_search_and_map_results() {
        WebSearchResults results = engineBuilder()
                .originalQuestion("What is new in DeepSeek V4?")
                .localization("127.0.0.1")
                .needContent(true)
                .build()
                .search(WebSearchRequest.builder()
                        .searchTerms("DeepSeek V4 release")
                        .maxResults(3)
                        .build());

        assertThat(requestPath.get()).isEqualTo("/v1/tools/search");
        assertThat(requestAuthorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get())
                .contains("\"query\":\"DeepSeek V4 release\"")
                .contains("\"count\":3")
                .contains("\"ori_question\":\"What is new in DeepSeek V4?\"")
                .contains("\"localization\":\"127.0.0.1\"")
                .contains("\"need_content\":true");

        assertThat(results.searchInformation().totalResults()).isEqualTo(1);
        List<WebSearchOrganicResult> mapped = results.results();
        assertThat(mapped).hasSize(1);
        assertThat(mapped.get(0).title()).isEqualTo("DeepSeek V4 model released");
        assertThat(mapped.get(0).url()).isEqualTo(URI.create("https://example.com/deepseek-v4"));
        assertThat(mapped.get(0).snippet())
                .isEqualTo("DeepSeek V4 is now available, with tool calling and long-context support.");
        assertThat(mapped.get(0).content()).isEqualTo("DeepSeek V4 is now generally available...");
        assertThat(mapped.get(0).metadata())
                .containsEntry(
                        "summary",
                        "DeepSeek has released the V4 model. The release adds improved tool calling and long-context support for agent workflows.")
                .containsEntry("site_name", "Example News")
                .containsEntry("site_icon", "https://example.com/favicon.ico")
                .containsEntry("date", "2026-06-01T00:00:00+00:00")
                .containsEntry("rerank_score", "2.4")
                .containsEntry("authority", "3");
    }

    @Test
    void should_omit_optional_request_fields_by_default() {
        engineBuilder().build().search("DeepSeek V4");

        assertThat(requestBody.get())
                .contains("\"query\":\"DeepSeek V4\"")
                .doesNotContain("count")
                .doesNotContain("ori_question")
                .doesNotContain("localization")
                .doesNotContain("need_content");
    }

    @Test
    void should_clamp_count_to_deepseek_limits() {
        engineBuilder()
                .build()
                .search(WebSearchRequest.builder()
                        .searchTerms("DeepSeek V4")
                        .maxResults(100)
                        .build());

        assertThat(requestBody.get()).contains("\"count\":50");
    }

    @Test
    void should_map_blank_content_to_null() {
        responseBody = """
                {
                  "results": [
                    {
                      "title": "DeepSeek V4",
                      "url": "https://example.com/deepseek-v4",
                      "snippet": "DeepSeek V4 is now available.",
                      "content": ""
                    }
                  ]
                }
                """;

        WebSearchResults results = engineBuilder().build().search("DeepSeek V4");

        assertThat(results.results()).hasSize(1);
        assertThat(results.results().get(0).content()).isNull();
    }

    @Test
    void should_return_empty_results_when_response_has_no_results() {
        responseBody = "{\"results\":[]}";

        assertThat(engineBuilder().build().search("DeepSeek V4").results()).isEmpty();
    }

    @Test
    void should_propagate_http_errors() {
        responseStatus = 401;

        assertThatThrownBy(() -> engineBuilder().build().search("DeepSeek V4")).isInstanceOf(HttpException.class);
    }

    @Test
    void search_async_should_return_results() throws Exception {
        WebSearchResults results = engineBuilder()
                .build()
                .searchAsync(WebSearchRequest.from("DeepSeek V4"))
                .get(5, TimeUnit.SECONDS);

        assertThat(results.results()).hasSize(1);
    }
}
