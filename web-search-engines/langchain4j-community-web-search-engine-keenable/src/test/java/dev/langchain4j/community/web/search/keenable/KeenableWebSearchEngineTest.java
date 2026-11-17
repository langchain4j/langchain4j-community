package dev.langchain4j.community.web.search.keenable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class KeenableWebSearchEngineTest {

    // Shape of a search response; "description" is deprecated and always empty, "credits" only comes with a key
    private static final String RESPONSE = "{"
            + "\"query\": \"What is LangChain4j?\","
            + "\"mode\": \"pro\","
            + "\"results\": ["
            + "  {\"title\": \"Introduction | LangChain4j\", \"url\": \"https://docs.langchain4j.dev/intro/\","
            + "   \"snippet\": \"The goal of LangChain4j is to simplify integrating LLMs into Java applications.\","
            + "   \"description\": \"\", \"published_at\": \"2026-10-30T00:00:00Z\","
            + "   \"acquired_at\": \"2026-11-03T08:57:22Z\"},"
            + "  {\"title\": \"No URL\", \"url\": \"\", \"snippet\": \"skipped\"},"
            + "  {\"title\": \" \", \"url\": \"https://example.com/no-title\"},"
            + "  {\"title\": \"LangChain4j on GitHub\", \"url\": \"https://github.com/langchain4j/langchain4j\","
            + "   \"snippet\": \"Java library for LLM apps\"}"
            + "],"
            + "\"credits\": {\"sku\": \"search.pro\", \"units\": 1, \"cost\": 1, \"paid\": false}"
            + "}";

    private HttpServer server;
    private volatile int responseStatus = 200;
    private volatile String responseBody = RESPONSE;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestApiKey = new AtomicReference<>();
    private final AtomicReference<String> requestTitle = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            requestTitle.set(exchange.getRequestHeaders().getFirst("X-Keenable-Title"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            byte[] body = responseBody.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private KeenableWebSearchEngine.Builder engineBuilder() {
        return KeenableWebSearchEngine.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort() + "/v1/");
    }

    @Test
    void should_use_public_endpoint_without_api_key() {
        engineBuilder().build().search("What is LangChain4j?");

        assertThat(requestPath.get()).isEqualTo("/v1/search/public");
        assertThat(requestApiKey.get()).isNull();
        assertThat(requestTitle.get()).isEqualTo("LangChain4j");
        assertThat(requestBody.get()).isEqualTo("{\"query\":\"What is LangChain4j?\"}");
    }

    @Test
    void should_treat_blank_api_key_as_keyless() {
        engineBuilder().apiKey(" ").build().search("What is LangChain4j?");

        assertThat(requestPath.get()).isEqualTo("/v1/search/public");
        assertThat(requestApiKey.get()).isNull();
    }

    @Test
    void should_use_keyed_endpoint_with_api_key() {
        engineBuilder().apiKey("test-key").appTitle("My App").build().search("What is LangChain4j?");

        assertThat(requestPath.get()).isEqualTo("/v1/search");
        assertThat(requestApiKey.get()).isEqualTo("test-key");
        assertThat(requestTitle.get()).isEqualTo("My App");
        assertThat(requestBody.get()).doesNotContain("test-key");
    }

    @Test
    void withApiKey_should_require_a_key() {
        assertThatThrownBy(() -> KeenableWebSearchEngine.withApiKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_reject_invalid_app_title() {
        assertThatThrownBy(() -> KeenableWebSearchEngine.builder().appTitle(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appTitle");
        assertThatThrownBy(() -> KeenableWebSearchEngine.builder()
                        .appTitle("My App\r\nX-Injected: 1")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appTitle");
    }

    @Test
    void builder_toString_should_mask_api_key() {
        assertThat(KeenableWebSearchEngine.builder().apiKey("secret-api-key").toString())
                .doesNotContain("secret-api-key")
                .contains("apiKey=********");
        assertThat(KeenableWebSearchEngine.builder().toString()).contains("apiKey=null");
    }

    @Test
    void should_send_optional_parameters() {
        KeenableWebSearchEngine engine = engineBuilder()
                .mode("realtime")
                .snippetMaxLength(500)
                .excludeDomains(List.of("pinterest.com", "example.org"))
                .build();

        engine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(5)
                .build());

        assertThat(requestBody.get())
                .contains("\"query\":\"What is LangChain4j?\"")
                .contains("\"mode\":\"realtime\"")
                .contains("\"max_results\":5")
                .contains("\"snippet_max_length\":500")
                .contains("\"exclude_domains\":[\"pinterest.com\",\"example.org\"]");
    }

    @Test
    void should_cap_max_results_at_50() {
        engineBuilder()
                .build()
                .search(WebSearchRequest.builder()
                        .searchTerms("What is LangChain4j?")
                        .maxResults(80)
                        .build());

        assertThat(requestBody.get()).contains("\"max_results\":50");
    }

    @Test
    void should_leave_out_empty_exclude_domains_and_not_map_safe_search() {
        engineBuilder()
                .excludeDomains(List.of())
                .build()
                .search(WebSearchRequest.builder()
                        .searchTerms("What is LangChain4j?")
                        .safeSearch(true)
                        .build());

        assertThat(requestBody.get()).doesNotContain("exclude_domains").doesNotContain("safe_search");
    }

    @Test
    void should_map_response() {
        WebSearchResults webSearchResults = engineBuilder().build().search("What is LangChain4j?");

        assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(2L);
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(2);

        WebSearchOrganicResult first = results.get(0);
        assertThat(first.title()).isEqualTo("Introduction | LangChain4j");
        assertThat(first.url()).isEqualTo(URI.create("https://docs.langchain4j.dev/intro/"));
        assertThat(first.snippet())
                .isEqualTo("The goal of LangChain4j is to simplify integrating LLMs into Java applications.");
        assertThat(first.content()).isNull();
        assertThat(first.metadata())
                .containsExactlyEntriesOf(Map.of(
                        "position", "1",
                        "published_at", "2026-10-30T00:00:00Z",
                        "acquired_at", "2026-11-03T08:57:22Z"));

        WebSearchOrganicResult second = results.get(1);
        assertThat(second.url()).isEqualTo(URI.create("https://github.com/langchain4j/langchain4j"));
        assertThat(second.metadata()).containsOnly(Map.entry("position", "2"));
    }

    @Test
    void should_fail_on_http_error() {
        responseStatus = 429;
        responseBody = "{\"error\": \"Too many requests\", \"message\": \"Rate limit exceeded\"}";

        assertThatThrownBy(() -> engineBuilder().build().search("What is LangChain4j?"))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void should_handle_response_without_results() {
        KeenableSearchResponse response =
                KeenableJsonUtils.fromJson("{\"query\": \"q\", \"mode\": \"pro\"}", KeenableSearchResponse.class);

        assertThat(KeenableWebSearchEngine.toWebSearchResults(response).results())
                .isEmpty();
    }

    @Test
    void searchAsync_should_return_the_same_results_as_the_blocking_search() throws Exception {
        KeenableWebSearchEngine engine = engineBuilder().build();
        WebSearchRequest request = WebSearchRequest.from("What is LangChain4j?");

        WebSearchResults async = engine.searchAsync(request).get(5, SECONDS);

        assertThat(requestPath.get()).isEqualTo("/v1/search/public");
        assertThat(async.results()).isEqualTo(engine.search(request).results());
    }

    @Test
    @Timeout(20)
    void searchAsync_cancellation_aborts_the_in_flight_http_call() throws Exception {
        try (ServerSocket silentServer = new ServerSocket(0)) {
            CountDownLatch accepted = new CountDownLatch(1);
            CompletableFuture<Boolean> clientDisconnected = new CompletableFuture<>();

            Thread serverThread = new Thread(() -> {
                try (Socket socket = silentServer.accept()) {
                    accepted.countDown();
                    InputStream in = socket.getInputStream();
                    byte[] buffer = new byte[1024];
                    while (in.read(buffer) != -1) {
                        // drain until the cancelled call closes the socket
                    }
                    clientDisconnected.complete(true);
                } catch (IOException e) {
                    clientDisconnected.complete(true); // a socket reset also means the client aborted
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            KeenableWebSearchEngine engine = KeenableWebSearchEngine.builder()
                    .baseUrl("http://localhost:" + silentServer.getLocalPort() + "/v1/")
                    .build();

            CompletableFuture<WebSearchResults> future =
                    engine.searchAsync(WebSearchRequest.from("What is LangChain4j?"));

            assertThat(accepted.await(5, SECONDS)).isTrue();
            assertThat(future).isNotDone();

            future.cancel(true);

            assertThat(clientDisconnected.get(5, SECONDS)).isTrue();
        }
    }
}
