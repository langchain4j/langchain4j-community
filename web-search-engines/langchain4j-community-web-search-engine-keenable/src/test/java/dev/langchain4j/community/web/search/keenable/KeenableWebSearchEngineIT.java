package dev.langchain4j.community.web.search.keenable;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs against the keyless public endpoint, so it needs no secrets.
 */
class KeenableWebSearchEngineIT extends WebSearchEngineIT {

    private static final int MAX_RESULTS = 3;

    WebSearchEngine webSearchEngine = KeenableWebSearchEngine.builder().build();

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
