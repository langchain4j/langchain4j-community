package dev.langchain4j.community.web.search.olostep;

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

@EnabledIfEnvironmentVariable(named = "OLOSTEP_API_KEY", matches = ".+")
class OlostepWebSearchEngineIT extends WebSearchEngineIT {

    // Tests that assert on the number of results request an explicit maximum instead of relying on the
    // server-side default for "limit".
    private static final int MAX_RESULTS = 3;

    WebSearchEngine webSearchEngine = OlostepWebSearchEngine.withApiKey(System.getenv("OLOSTEP_API_KEY"));

    @Test
    void should_search_with_scraped_content() {

        // given
        OlostepWebSearchEngine olostepWebSearchEngine = OlostepWebSearchEngine.builder()
                .apiKey(System.getenv("OLOSTEP_API_KEY"))
                .scrapeContent(true)
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = olostepWebSearchEngine.search(request);

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
    void should_restrict_results_to_included_domains() {

        // given
        OlostepWebSearchEngine olostepWebSearchEngine = OlostepWebSearchEngine.builder()
                .apiKey(System.getenv("OLOSTEP_API_KEY"))
                .includeDomains(List.of("github.com"))
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("LangChain4j")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = olostepWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
        results.forEach(result -> assertThat(result.url().getHost()).endsWith("github.com"));
    }

    @Test
    void searchAsync_should_return_results() throws Exception {

        // given
        OlostepWebSearchEngine olostepWebSearchEngine =
                OlostepWebSearchEngine.withApiKey(System.getenv("OLOSTEP_API_KEY"));

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults =
                olostepWebSearchEngine.searchAsync(request).get(30, TimeUnit.SECONDS);

        // then
        assertThat(webSearchResults.results()).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
