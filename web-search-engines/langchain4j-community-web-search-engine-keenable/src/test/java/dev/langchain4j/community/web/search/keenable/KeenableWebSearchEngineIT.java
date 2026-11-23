package dev.langchain4j.community.web.search.keenable;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs against the keyless public endpoint, so it needs no secrets.
 * That endpoint is rate-limited per IP address, so on a shared CI egress IP it can answer {@code 429}.
 * A preflight probe skips the class in that case (and without network), since a {@code 429} here is not a code failure.
 */
class KeenableWebSearchEngineIT extends WebSearchEngineIT {

    private static final int MAX_RESULTS = 3;

    WebSearchEngine webSearchEngine = KeenableWebSearchEngine.builder().build();

    @BeforeAll
    static void keylessEndpointAvailable() {
        Assumptions.assumeTrue(
                probeStatus() == 200, "Skipping Keenable tests: keyless endpoint unreachable or rate-limited");
    }

    private static int probeStatus() {
        try {
            HttpRequest probe = HttpRequest.newBuilder(URI.create("https://api.keenable.ai/v1/search/public"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Keenable-Title", "LangChain4j")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"LangChain4j\",\"max_results\":1}"))
                    .build();
            return HttpClient.newHttpClient()
                    .send(probe, HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    @Test
    void should_return_position_metadata() {

        // given
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = webSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
        results.forEach(result -> assertThat(result.metadata()).containsKey("position"));
    }

    @Test
    void searchAsync_should_return_results() throws Exception {

        // given
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = webSearchEngine.searchAsync(request).get(60, TimeUnit.SECONDS);

        // then
        assertThat(webSearchResults.results()).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KEENABLE_API_KEY", matches = ".+")
    void should_search_with_api_key() {

        // given
        KeenableWebSearchEngine keyedEngine = KeenableWebSearchEngine.withApiKey(System.getenv("KEENABLE_API_KEY"));

        // when
        WebSearchResults webSearchResults = keyedEngine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build());

        // then
        assertThat(webSearchResults.results()).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
