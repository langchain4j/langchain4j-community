package dev.langchain4j.community.web.search.duckduckgo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DuckDuckGoWebSearchEngineIT {
    private static final int EXPECTED_MAX_RESULTS = 7;
    private static DuckDuckGoWebSearchEngine engine;

    @BeforeAll
    static void initEngine() {
        Assumptions.assumeTrue(isNetworkAvailable(), "Skipping DuckDuckGo tests: no network");

        engine = DuckDuckGoWebSearchEngine.builder()
                .duration(Duration.ofSeconds(30))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private static boolean isNetworkAvailable() {
        try {
            var url = new java.net.URL("https://api.duckduckgo.com");
            var conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.connect();
            conn.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void should_search_async() throws Exception {
        if (engine == null) {
            return;
        }

        WebSearchRequest req1 =
                WebSearchRequest.builder().searchTerms("Java").maxResults(2).build();

        WebSearchRequest req2 = WebSearchRequest.builder()
                .searchTerms("Javascript")
                .maxResults(2)
                .build();

        CompletableFuture<WebSearchResults> f1 = engine.searchAsync(req1);
        CompletableFuture<WebSearchResults> f2 = engine.searchAsync(req2);

        CompletableFuture.allOf(f1, f2).get(30, TimeUnit.SECONDS);

        assertThat(f1.get().results()).isNotEmpty();
        assertThat(f2.get().results()).isNotEmpty();
    }

    @Test
    void should_search_with_language_and_additional_params() {
        if (engine == null) {
            return;
        }

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("machine learning")
                .language("en-US")
                .safeSearch(true)
                .additionalParams(Map.of("df", "m"))
                .maxResults(5)
                .build();

        WebSearchResults results = engine.search(request);

        assertThat(results).isNotNull();
        assertThat(results.results()).isNotEmpty();
        assertThat(results.results().get(0).title()).isNotBlank();
    }

    @Test
    void should_search() {
        if (engine == null) {
            return;
        }

        WebSearchResults webSearchResults = engine.search("Java");
        List<WebSearchOrganicResult> results = webSearchResults.results();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).isNotBlank();
    }

    @Test
    void should_search_with_max_results() {
        if (engine == null) {
            return;
        }

        int maxResults = EXPECTED_MAX_RESULTS;
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is Artificial Intelligence?")
                .maxResults(maxResults)
                .build();
        WebSearchResults webSearchResults = engine.search(request);
        List<WebSearchOrganicResult> results = webSearchResults.results();
        Assertions.assertThat(results).hasSizeLessThanOrEqualTo(maxResults);
    }
}
