package dev.langchain4j.community.web.search.firecrawl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.LangChain4jException;
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

class FirecrawlWebSearchEngineTest {

    // Shape of a default search response, without scrapeOptions there is no "markdown" field
    private static final String RESPONSE = "{"
            + "\"success\": true,"
            + "\"creditsUsed\": 1,"
            + "\"id\": \"abc\","
            + "\"data\": {"
            + "  \"web\": ["
            + "    {\"url\": \"https://docs.langchain4j.dev/intro/\", \"title\": \"Introduction | LangChain4j\","
            + "     \"description\": \"The goal of LangChain4j is to simplify integrating LLMs into Java applications.\","
            + "     \"position\": 1, \"category\": \"docs\"},"
            + "    {\"url\": \"\", \"title\": \"No URL\", \"description\": \"skipped\", \"position\": 2},"
            + "    {\"url\": \"https://example.com/no-title\", \"title\": \" \", \"position\": 3},"
            + "    {\"url\": \"https://github.com/langchain4j/langchain4j\", \"title\": \"LangChain4j on GitHub\","
            + "     \"description\": \"Java library for LLM apps\", \"position\": 4}"
            + "  ],"
            + "  \"news\": [{\"title\": \"ignored\"}]"
            + "}}";

    // Shape of a search response with scrapeOptions, each web result also carries "markdown" and "metadata"
    private static final String SCRAPE_RESPONSE = "{"
            + "\"success\": true,"
            + "\"creditsUsed\": 2,"
            + "\"data\": {"
            + "  \"web\": ["
            + "    {\"url\": \"https://docs.langchain4j.dev/intro/\", \"title\": \"Introduction | LangChain4j\","
            + "     \"description\": \"The goal of LangChain4j is to simplify integrating LLMs into Java applications.\","
            + "     \"position\": 1, \"markdown\": \"# Introduction\","
            + "     \"metadata\": {\"ogTitle\": \"Introduction\", \"statusCode\": 200}}"
            + "  ]"
            + "}}";

    private HttpServer server;
    private volatile int responseStatus = 200;
    private volatile String responseBody = RESPONSE;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
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

    private FirecrawlWebSearchEngine.Builder engineBuilder() {
        return FirecrawlWebSearchEngine.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort() + "/v2/")
                .apiKey("test-key");
    }

    @Test
    void should_require_api_key() {
        assertThatThrownBy(() -> FirecrawlWebSearchEngine.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> FirecrawlWebSearchEngine.withApiKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void builder_toString_should_mask_api_key() {
        assertThat(FirecrawlWebSearchEngine.builder().apiKey("secret-api-key").toString())
                .doesNotContain("secret-api-key")
                .contains("apiKey=********");
        assertThat(FirecrawlWebSearchEngine.builder().toString()).contains("apiKey=null");
    }

    @Test
    void should_send_minimal_request() {
        engineBuilder().build().search("What is LangChain4j?");

        assertThat(requestPath.get()).isEqualTo("/v2/search");
        assertThat(requestAuthorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get())
                .contains("\"query\":\"What is LangChain4j?\"")
                .contains("\"sources\":[\"web\"]")
                .contains("\"origin\":\"langchain4j\"")
                .doesNotContain("limit")
                .doesNotContain("scrapeOptions")
                .doesNotContain("location")
                .doesNotContain("tbs")
                .doesNotContain("test-key");
    }

    @Test
    void should_send_optional_parameters() {
        FirecrawlWebSearchEngine engine = engineBuilder()
                .location("Germany")
                .timeBasedFilter("qdr:w")
                .scrapeContent(true)
                .build();

        engine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(3)
                .build());

        assertThat(requestBody.get())
                .contains("\"limit\":3")
                .contains("\"location\":\"Germany\"")
                .contains("\"tbs\":\"qdr:w\"")
                .contains("\"scrapeOptions\":{\"formats\":[{\"type\":\"markdown\"}]}");
    }

    @Test
    void should_map_response_without_content_by_default() {
        WebSearchResults webSearchResults = engineBuilder().build().search("What is LangChain4j?");

        assertThat(requestBody.get()).doesNotContain("scrapeOptions");

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(2);
        assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(2L);

        WebSearchOrganicResult first = results.get(0);
        assertThat(first.title()).isEqualTo("Introduction | LangChain4j");
        assertThat(first.url()).isEqualTo(URI.create("https://docs.langchain4j.dev/intro/"));
        assertThat(first.snippet()).startsWith("The goal of LangChain4j");
        assertThat(first.content()).isNull();
        assertThat(first.metadata()).containsExactlyEntriesOf(Map.of("position", "1"));

        WebSearchOrganicResult second = results.get(1);
        assertThat(second.url()).isEqualTo(URI.create("https://github.com/langchain4j/langchain4j"));
        assertThat(second.content()).isNull();
        assertThat(second.metadata()).containsEntry("position", "4");
    }

    @Test
    void should_map_scraped_markdown_to_content() {
        responseBody = SCRAPE_RESPONSE;

        WebSearchResults webSearchResults =
                engineBuilder().scrapeContent(true).build().search("What is LangChain4j?");

        assertThat(requestBody.get()).contains("\"scrapeOptions\":{\"formats\":[{\"type\":\"markdown\"}]}");

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("# Introduction");
        assertThat(results.get(0).metadata()).containsExactlyEntriesOf(Map.of("position", "1"));
    }

    @Test
    void should_fail_when_success_is_false() {
        responseBody = "{\"success\": false, \"error\": \"Insufficient credits\"}";

        assertThatThrownBy(() -> engineBuilder().build().search("What is LangChain4j?"))
                .isInstanceOf(LangChain4jException.class)
                .hasMessageContaining("Insufficient credits");
    }

    @Test
    void should_fail_on_http_error() {
        responseStatus = 401;
        responseBody = "{\"success\": false, \"error\": \"Unauthorized: Invalid token\"}";

        assertThatThrownBy(() -> engineBuilder().build().search("What is LangChain4j?"))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void should_handle_response_without_results() {
        FirecrawlSearchResponse response =
                FirecrawlJsonUtils.fromJson("{\"success\": true, \"data\": {}}", FirecrawlSearchResponse.class);

        assertThat(FirecrawlWebSearchEngine.toWebSearchResults(response).results())
                .isEmpty();
    }

    @Test
    void searchAsync_should_return_the_same_results_as_the_blocking_search() throws Exception {
        FirecrawlWebSearchEngine engine = engineBuilder().build();
        WebSearchRequest request = WebSearchRequest.from("What is LangChain4j?");

        WebSearchResults async = engine.searchAsync(request).get(5, SECONDS);

        assertThat(requestPath.get()).isEqualTo("/v2/search");
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

            FirecrawlWebSearchEngine engine = FirecrawlWebSearchEngine.builder()
                    .baseUrl("http://localhost:" + silentServer.getLocalPort() + "/v2/")
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
