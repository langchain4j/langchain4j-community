package dev.langchain4j.community.web.search.olostep;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class OlostepWebSearchEngineTest {

    // Shape of a default search response, without scrape_options there is no "markdown_content" field.
    // The top-level fields other than "result" are not mapped and must be ignored.
    private static final String RESPONSE = "{"
            + "\"id\": \"search_9bi0sbj9xa\","
            + "\"object\": \"search\","
            + "\"created\": 1760327323,"
            + "\"metadata\": {},"
            + "\"query\": \"What is LangChain4j?\","
            + "\"credits_consumed\": 5,"
            + "\"storage\": {\"expires_in\": \"7d\"},"
            + "\"result\": {"
            + "  \"json_content\": \"...\","
            + "  \"json_hosted_url\": \"https://olostep-storage.s3.us-east-1.amazonaws.com/search_9bi0sbj9xa.json\","
            + "  \"size_exceeded\": false,"
            + "  \"credits_consumed\": 5,"
            + "  \"links\": ["
            + "    {\"url\": \"https://docs.langchain4j.dev/intro/\", \"title\": \"Introduction | LangChain4j\","
            + "     \"description\": \"LangChain4j simplifies integrating LLMs into Java applications.\"},"
            + "    {\"url\": \"https://example.com/no-title\", \"title\": null, \"description\": \"skipped\"},"
            + "    {\"url\": \"https://github.com/langchain4j/langchain4j\", \"title\": \"LangChain4j on GitHub\","
            + "     \"description\": null}"
            + "  ]"
            + "}}";

    // Shape of a search response with scrape_options, each link also carries "markdown_content",
    // which is null for a page that could not be scraped in time
    private static final String SCRAPE_RESPONSE = "{"
            + "\"id\": \"search_xyyqe83rvj\","
            + "\"object\": \"search\","
            + "\"credits_consumed\": 7,"
            + "\"result\": {"
            + "  \"size_exceeded\": false,"
            + "  \"links\": ["
            + "    {\"url\": \"https://docs.langchain4j.dev/intro/\", \"title\": \"Introduction | LangChain4j\","
            + "     \"description\": \"LangChain4j simplifies integrating LLMs into Java applications.\","
            + "     \"markdown_content\": \"# Introduction\"},"
            + "    {\"url\": \"https://github.com/langchain4j/langchain4j\", \"title\": \"LangChain4j on GitHub\","
            + "     \"description\": \"Java library for LLM apps\", \"markdown_content\": null}"
            + "  ]"
            + "}}";

    // Body Olostep returns for an invalid API key
    private static final String INVALID_API_KEY_RESPONSE = "{"
            + "\"object\": \"error\","
            + "\"code\": \"invalid_api_key\","
            + "\"status\": 401,"
            + "\"title\": \"Invalid or missing API key\","
            + "\"detail\": \"The provided API key is invalid.\""
            + "}";

    private HttpServer server;
    private volatile int responseStatus = 200;
    private volatile String responseBody = RESPONSE;
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            requestPath.set(exchange.getRequestURI().getPath());
            requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private OlostepWebSearchEngine.Builder engineBuilder() {
        return OlostepWebSearchEngine.builder().baseUrl(serverUrl()).apiKey("test-key");
    }

    @Test
    void should_require_api_key() {
        assertThatThrownBy(() -> OlostepWebSearchEngine.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> OlostepWebSearchEngine.withApiKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_reject_null_request() {
        OlostepWebSearchEngine engine = engineBuilder().build();

        assertThatThrownBy(() -> engine.search((WebSearchRequest) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webSearchRequest");
        assertThatThrownBy(() -> engine.searchAsync(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webSearchRequest");
    }

    @Test
    void should_send_minimal_request() {
        engineBuilder().build().search("What is LangChain4j?");

        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(requestPath.get()).isEqualTo("/v1/searches");
        assertThat(requestAuthorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get()).isEqualTo("{\"query\":\"What is LangChain4j?\"}");
    }

    @Test
    void should_send_optional_parameters() {
        OlostepWebSearchEngine engine = engineBuilder()
                .includeDomains(List.of("docs.langchain4j.dev", "github.com"))
                .excludeDomains(List.of("pinterest.com"))
                .scrapeContent(true)
                .build();

        engine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(3)
                .build());

        assertThat(requestBody.get())
                .contains("\"query\":\"What is LangChain4j?\"")
                .contains("\"limit\":3")
                .contains("\"include_domains\":[\"docs.langchain4j.dev\",\"github.com\"]")
                .contains("\"exclude_domains\":[\"pinterest.com\"]")
                .contains("\"scrape_options\":{\"formats\":[\"markdown\"]}");
    }

    @Test
    void should_not_send_empty_domain_lists() {
        engineBuilder()
                .includeDomains(List.of())
                .excludeDomains(List.of())
                .build()
                .search("What is LangChain4j?");

        assertThat(requestBody.get()).isEqualTo("{\"query\":\"What is LangChain4j?\"}");
    }

    @Test
    void should_clamp_max_results_to_supported_range() {
        OlostepWebSearchEngine engine = engineBuilder().build();

        engine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(100)
                .build());
        assertThat(requestBody.get()).contains("\"limit\":25");

        engine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(0)
                .build());
        assertThat(requestBody.get()).contains("\"limit\":1");
    }

    @Test
    void should_append_searches_path_to_base_url_with_trailing_slash() {
        OlostepWebSearchEngine.builder()
                .baseUrl(serverUrl() + "/")
                .apiKey("test-key")
                .build()
                .search("What is LangChain4j?");

        assertThat(requestPath.get()).isEqualTo("/v1/searches");
    }

    @Test
    void should_map_response_without_content_by_default() {
        WebSearchResults webSearchResults = engineBuilder().build().search("What is LangChain4j?");

        assertThat(requestBody.get()).doesNotContain("scrape_options");

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(2);
        assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(2L);

        WebSearchOrganicResult first = results.get(0);
        assertThat(first.title()).isEqualTo("Introduction | LangChain4j");
        assertThat(first.url()).isEqualTo(URI.create("https://docs.langchain4j.dev/intro/"));
        assertThat(first.snippet()).isEqualTo("LangChain4j simplifies integrating LLMs into Java applications.");
        assertThat(first.content()).isNull();

        WebSearchOrganicResult second = results.get(1);
        assertThat(second.title()).isEqualTo("LangChain4j on GitHub");
        assertThat(second.url()).isEqualTo(URI.create("https://github.com/langchain4j/langchain4j"));
        assertThat(second.snippet()).isNull();
        assertThat(second.content()).isNull();
    }

    @Test
    void should_map_scraped_markdown_to_content() {
        responseBody = SCRAPE_RESPONSE;

        WebSearchResults webSearchResults =
                engineBuilder().scrapeContent(true).build().search("What is LangChain4j?");

        assertThat(requestBody.get()).contains("\"scrape_options\":{\"formats\":[\"markdown\"]}");

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).content()).isEqualTo("# Introduction");
        assertThat(results.get(1).snippet()).isEqualTo("Java library for LLM apps");
        assertThat(results.get(1).content()).isNull();
    }

    @Test
    void should_fail_on_http_error() {
        responseStatus = 401;
        responseBody = INVALID_API_KEY_RESPONSE;

        assertThatThrownBy(() -> engineBuilder().build().search("What is LangChain4j?"))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("invalid_api_key");
    }

    @Test
    void should_handle_response_without_results() {
        OlostepSearchResponse emptyResponse = OlostepJsonUtils.fromJson("{}", OlostepSearchResponse.class);
        OlostepSearchResponse emptyLinks =
                OlostepJsonUtils.fromJson("{\"result\": {\"links\": []}}", OlostepSearchResponse.class);

        assertThat(OlostepWebSearchEngine.toWebSearchResults(emptyResponse).results())
                .isEmpty();
        assertThat(OlostepWebSearchEngine.toWebSearchResults(emptyLinks).results())
                .isEmpty();
    }

    @Test
    void searchAsync_should_return_the_same_results_as_the_blocking_search() throws Exception {
        OlostepWebSearchEngine engine = engineBuilder().build();
        WebSearchRequest request = WebSearchRequest.from("What is LangChain4j?");

        WebSearchResults async = engine.searchAsync(request).get(5, SECONDS);

        assertThat(requestPath.get()).isEqualTo("/v1/searches");
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

            OlostepWebSearchEngine engine = OlostepWebSearchEngine.builder()
                    .baseUrl("http://localhost:" + silentServer.getLocalPort())
                    .apiKey("test-key")
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
