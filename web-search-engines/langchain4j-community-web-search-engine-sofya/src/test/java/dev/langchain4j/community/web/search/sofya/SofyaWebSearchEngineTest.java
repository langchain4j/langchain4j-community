package dev.langchain4j.community.web.search.sofya;

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

class SofyaWebSearchEngineTest {

    // Shape of a "basic" search response: fetched results carry the page text in "content"
    private static final String RESPONSE = "{"
            + "\"query\": \"What is LangChain4j?\","
            + "\"answer\": null,"
            + "\"results\": ["
            + "  {\"title\": \"Introduction | LangChain4j\", \"url\": \"https://docs.langchain4j.dev/intro/\","
            + "   \"content\": \"# Introduction\\n\\nWelcome!\","
            + "   \"description\": \"The goal of LangChain4j is to simplify integrating LLMs into Java applications.\","
            + "   \"fetched\": true, \"published_date\": null, \"sublinks\": [], \"table\": {}},"
            + "  {\"title\": \"No URL\", \"url\": \"\", \"content\": \"skipped\", \"fetched\": false},"
            + "  {\"title\": \" \", \"url\": \"https://example.com/no-title\", \"content\": \"skipped\", \"fetched\": false},"
            + "  {\"title\": \"LangChain4j on GitHub\", \"url\": \"https://github.com/langchain4j/langchain4j\","
            + "   \"content\": \"Java library for LLM apps\", \"description\": \"Java library for LLM apps\","
            + "   \"fetched\": false, \"published_date\": \"2023-06-20\", \"sublinks\": [], \"table\": {}}"
            + "],"
            + "\"search_depth\": \"basic\","
            + "\"topic\": \"general\","
            + "\"elapsed_ms\": 4210.5,"
            + "\"credits_used\": 2,"
            + "\"credits_remaining\": 998,"
            + "\"altered_query\": null,"
            + "\"relaxed_query\": null"
            + "}";

    private static final String ANSWER_RESPONSE = "{"
            + "\"query\": \"What is LangChain4j?\","
            + "\"answer\": \"LangChain4j is a Java library for building LLM applications.\","
            + "\"results\": ["
            + "  {\"title\": \"Introduction | LangChain4j\", \"url\": \"https://docs.langchain4j.dev/intro/\","
            + "   \"content\": \"The goal of LangChain4j is to simplify integrating LLMs into Java applications.\","
            + "   \"description\": \"The goal of LangChain4j is to simplify integrating LLMs into Java applications.\","
            + "   \"fetched\": false}"
            + "],"
            + "\"search_depth\": \"snippets\","
            + "\"topic\": \"general\","
            + "\"credits_used\": 3,"
            + "\"credits_remaining\": 995,"
            + "\"altered_query\": \"What is LangChain4j\","
            + "\"relaxed_query\": null"
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

    private SofyaWebSearchEngine.Builder engineBuilder() {
        return SofyaWebSearchEngine.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort() + "/")
                .apiKey("test-key");
    }

    @Test
    void should_require_api_key() {
        assertThatThrownBy(() -> SofyaWebSearchEngine.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> SofyaWebSearchEngine.withApiKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void builder_toString_should_mask_api_key() {
        assertThat(SofyaWebSearchEngine.builder().apiKey("secret-api-key").toString())
                .doesNotContain("secret-api-key")
                .contains("apiKey=********");
        assertThat(SofyaWebSearchEngine.builder().toString()).contains("apiKey=null");
    }

    @Test
    void should_send_minimal_request() {
        engineBuilder().build().search("What is LangChain4j?");

        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(requestPath.get()).isEqualTo("/v1/search");
        assertThat(requestAuthorization.get()).isEqualTo("Bearer test-key");
        assertThat(requestBody.get()).isEqualTo("{\"query\":\"What is LangChain4j?\",\"search_depth\":\"snippets\"}");
    }

    @Test
    void should_send_optional_parameters() {
        SofyaWebSearchEngine engine = engineBuilder()
                .includeAnswer(true)
                .includeDomains(List.of("docs.langchain4j.dev", "github.com"))
                .excludeDomains(List.of("pinterest.com"))
                .topic(SofyaSearchTopic.NEWS)
                .freshness("week")
                .build();

        engine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(3)
                .build());

        assertThat(requestBody.get())
                .contains("\"query\":\"What is LangChain4j?\"")
                .contains("\"max_results\":3")
                .contains("\"search_depth\":\"snippets\"")
                .contains("\"include_answer\":true")
                .contains("\"include_domains\":[\"docs.langchain4j.dev\",\"github.com\"]")
                .contains("\"exclude_domains\":[\"pinterest.com\"]")
                .contains("\"topic\":\"news\"")
                .contains("\"freshness\":\"week\"")
                .doesNotContain("test-key");
    }

    @Test
    void should_not_send_empty_domain_lists() {
        engineBuilder()
                .includeDomains(List.of())
                .excludeDomains(List.of())
                .build()
                .search("What is LangChain4j?");

        assertThat(requestBody.get()).doesNotContain("include_domains").doesNotContain("exclude_domains");
    }

    @Test
    void should_map_fetched_page_text_to_content() {
        WebSearchResults webSearchResults =
                engineBuilder().searchDepth(SofyaSearchDepth.BASIC).build().search("What is LangChain4j?");

        assertThat(requestBody.get()).contains("\"search_depth\":\"basic\"");

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(2);
        assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(2L);
        assertThat(webSearchResults.searchMetadata()).containsExactlyEntriesOf(Map.of("query", "What is LangChain4j?"));

        WebSearchOrganicResult first = results.get(0);
        assertThat(first.title()).isEqualTo("Introduction | LangChain4j");
        assertThat(first.url()).isEqualTo(URI.create("https://docs.langchain4j.dev/intro/"));
        assertThat(first.snippet()).startsWith("The goal of LangChain4j");
        assertThat(first.content()).isEqualTo("# Introduction\n\nWelcome!");
        assertThat(first.metadata()).isEmpty();

        // a page that was not fetched in time has no content, only its snippet
        WebSearchOrganicResult second = results.get(1);
        assertThat(second.url()).isEqualTo(URI.create("https://github.com/langchain4j/langchain4j"));
        assertThat(second.snippet()).isEqualTo("Java library for LLM apps");
        assertThat(second.content()).isNull();
        assertThat(second.metadata()).containsExactlyEntriesOf(Map.of("published_date", "2023-06-20"));
    }

    @Test
    void should_prepend_answer_and_expose_search_metadata() {
        responseBody = ANSWER_RESPONSE;

        WebSearchResults webSearchResults = engineBuilder()
                .searchDepth(SofyaSearchDepth.SNIPPETS)
                .includeAnswer(true)
                .build()
                .search("What is LangChain4j?");

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(2);
        // the answer is not counted as a search result
        assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(1L);

        WebSearchOrganicResult answer = results.get(0);
        assertThat(answer.title()).isEqualTo("Sofya answer");
        assertThat(answer.snippet()).isEqualTo("LangChain4j is a Java library for building LLM applications.");
        assertThat(answer.content()).isNull();
        assertThat(answer.metadata()).containsExactlyEntriesOf(Map.of("result_type", "answer"));

        WebSearchOrganicResult result = results.get(1);
        assertThat(result.snippet()).startsWith("The goal of LangChain4j");
        assertThat(result.content()).isNull();

        assertThat(webSearchResults.searchMetadata())
                .containsEntry("answer", "LangChain4j is a Java library for building LLM applications.")
                .containsEntry("altered_query", "What is LangChain4j")
                .doesNotContainKey("relaxed_query");
    }

    @Test
    void should_use_content_as_snippet_when_description_is_missing() {
        SofyaSearchResponse response = SofyaJsonUtils.fromJson(
                "{\"results\": [{\"title\": \"T\", \"url\": \"https://example.com/\", \"content\": \"snippet text\","
                        + " \"fetched\": false}]}",
                SofyaSearchResponse.class);

        WebSearchOrganicResult result =
                SofyaWebSearchEngine.toWebSearchResults(response).results().get(0);
        assertThat(result.snippet()).isEqualTo("snippet text");
        assertThat(result.content()).isNull();
    }

    @Test
    void should_fail_on_http_error() {
        responseStatus = 401;
        responseBody = "{\"detail\": \"Invalid API key\", \"error\": \"unauthorized\"}";

        assertThatThrownBy(() -> engineBuilder().build().search("What is LangChain4j?"))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    void should_handle_response_without_results() {
        SofyaSearchResponse response = SofyaJsonUtils.fromJson("{\"results\": []}", SofyaSearchResponse.class);

        assertThat(SofyaWebSearchEngine.toWebSearchResults(response).results()).isEmpty();
    }

    @Test
    void searchAsync_should_return_the_same_results_as_the_blocking_search() throws Exception {
        SofyaWebSearchEngine engine = engineBuilder().build();
        WebSearchRequest request = WebSearchRequest.from("What is LangChain4j?");

        WebSearchResults async = engine.searchAsync(request).get(5, SECONDS);

        assertThat(requestPath.get()).isEqualTo("/v1/search");
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

            SofyaWebSearchEngine engine = SofyaWebSearchEngine.builder()
                    .baseUrl("http://localhost:" + silentServer.getLocalPort() + "/")
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
