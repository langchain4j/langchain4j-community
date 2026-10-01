package dev.langchain4j.community.web.search.firecrawl;

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

@EnabledIfEnvironmentVariable(named = "FIRECRAWL_API_KEY", matches = ".+")
class FirecrawlWebSearchEngineIT extends WebSearchEngineIT {

    // Tests that assert on the number of results request an explicit maximum instead of relying on the
    // server-side default for "limit".
    private static final int MAX_RESULTS = 3;

    WebSearchEngine webSearchEngine = FirecrawlWebSearchEngine.withApiKey(System.getenv("FIRECRAWL_API_KEY"));

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
        results.forEach(result -> assertThat(result.metadata()).containsOnlyKeys("position"));
    }

    @Test
    void should_search_with_scraped_content() {

        // given
        FirecrawlWebSearchEngine firecrawlWebSearchEngine = FirecrawlWebSearchEngine.builder()
                .apiKey(System.getenv("FIRECRAWL_API_KEY"))
                .scrapeContent(true)
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = firecrawlWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
        });
        assertThat(results)
                .anyMatch(result -> result.content() != null && result.content().contains("LangChain4j"));
    }

    @Test
    void searchAsync_should_return_results() throws Exception {

        // given
        FirecrawlWebSearchEngine firecrawlWebSearchEngine =
                FirecrawlWebSearchEngine.withApiKey(System.getenv("FIRECRAWL_API_KEY"));

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults =
                firecrawlWebSearchEngine.searchAsync(request).get(30, TimeUnit.SECONDS);

        // then
        assertThat(webSearchResults.results()).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
