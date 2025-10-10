package dev.langchain4j.community.web.search.duckduckgo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuckDuckGoWebSearchEngineIT extends WebSearchEngineIT {
    WebSearchEngine webSearchEngine;

    @BeforeEach
    void setup() throws InterruptedException {
        // we add small sleep duration to avoid hitting rate limit in test runs
        Thread.sleep(3000);

        webSearchEngine = DuckDuckGoWebSearchEngine.builder()
                .duration(Duration.ofSeconds(15))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Test
    void duckDuckGo_should_search_with_max_results() {
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("machine learning Python libraries")
                .maxResults(3)
                .build();

        WebSearchResults webSearchResults = searchEngine().search(request);

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(3);

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
        });
    }

    @Test
    void should_search_async_in_parallel() throws Exception {
        DuckDuckGoWebSearchEngine searchEngine = DuckDuckGoWebSearchEngine.builder()
                .duration(Duration.ofSeconds(30))
                .build();

        WebSearchRequest req1 =
                WebSearchRequest.builder().searchTerms("Java").maxResults(2).build();

        WebSearchRequest req2 = WebSearchRequest.builder()
                .searchTerms("Javascript")
                .maxResults(2)
                .build();

        CompletableFuture<WebSearchResults> f1 = searchEngine.searchAsync(req1);
        CompletableFuture<WebSearchResults> f2 = searchEngine.searchAsync(req2);

        CompletableFuture.allOf(f1, f2).get(30, TimeUnit.SECONDS);

        assertThat(f1.get().results()).isNotEmpty();
        assertThat(f2.get().results()).isNotEmpty();
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return request -> {
            WebSearchResults results = webSearchEngine.search(request);
            if (results.results().isEmpty()) {
                try {
                    Thread.sleep(5000);
                    return webSearchEngine.search(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return results;
        };
    }
}
