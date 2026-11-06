package dev.langchain4j.community.web.search.sofya;

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

@EnabledIfEnvironmentVariable(named = "SOFYA_API_KEY", matches = ".+")
class SofyaWebSearchEngineIT extends WebSearchEngineIT {

    // Tests that assert on the number of results request an explicit maximum instead of relying on the
    // server-side default for "max_results".
    private static final int MAX_RESULTS = 3;

    WebSearchEngine webSearchEngine = engineBuilder().build();

    private static SofyaWebSearchEngine.Builder engineBuilder() {
        SofyaWebSearchEngine.Builder builder = SofyaWebSearchEngine.builder().apiKey(System.getenv("SOFYA_API_KEY"));
        String baseUrl = System.getenv("SOFYA_BASE_URL");
        return baseUrl == null || baseUrl.isBlank() ? builder : builder.baseUrl(baseUrl);
    }

    @Test
    void should_return_page_content() {

        // given
        SofyaWebSearchEngine sofyaWebSearchEngine =
                engineBuilder().searchDepth(SofyaSearchDepth.BASIC).build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = sofyaWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
        assertThat(results)
                .anyMatch(result -> result.content() != null && result.content().contains("LangChain4j"));
    }

    @Test
    void should_search_snippets_with_answer() {

        // given
        SofyaWebSearchEngine sofyaWebSearchEngine =
                engineBuilder().includeAnswer(true).build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = sofyaWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSizeGreaterThan(1).hasSizeLessThanOrEqualTo(MAX_RESULTS + 1);
        assertThat(results.get(0).metadata()).containsEntry("result_type", "answer");
        assertThat(results.get(0).snippet()).isNotBlank();
        assertThat(webSearchResults.searchMetadata()).containsKey("answer");
        results.subList(1, results.size()).forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.content()).isNull();
        });
    }

    @Test
    void should_only_return_included_domains() {

        // given
        SofyaWebSearchEngine sofyaWebSearchEngine =
                engineBuilder().includeDomains(List.of("github.com")).build();

        // when
        WebSearchResults webSearchResults = sofyaWebSearchEngine.search(WebSearchRequest.builder()
                .searchTerms("LangChain4j")
                .maxResults(MAX_RESULTS)
                .build());

        // then
        assertThat(webSearchResults.results())
                .isNotEmpty()
                .allMatch(result -> result.url().getHost().endsWith("github.com"));
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

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
