package dev.langchain4j.community.web.search.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeepSeekWebSearchEngineIT extends WebSearchEngineIT {

    private static final int MAX_RESULTS = 3;
    private static final String DEEPSEEK_API_KEY = System.getenv("DEEPSEEK_API_KEY");

    @BeforeAll
    static void checkApiKey() {
        Assumptions.assumeTrue(
                DEEPSEEK_API_KEY != null && !DEEPSEEK_API_KEY.isBlank(),
                "Skipping DeepSeek integration tests: DEEPSEEK_API_KEY is not set");
    }

    private final WebSearchEngine webSearchEngine = DeepSeekWebSearchEngine.builder()
            .apiKey(DEEPSEEK_API_KEY)
            .timeout(Duration.ofSeconds(60))
            .build();

    @Test
    void should_return_search_results_with_metadata() {
        WebSearchResults results = webSearchEngine.search(WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build());

        List<WebSearchOrganicResult> organicResults = results.results();
        assertThat(organicResults).isNotEmpty().hasSizeLessThanOrEqualTo(MAX_RESULTS);
        organicResults.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
        });
    }

    @Test
    void should_search_with_content() {
        WebSearchResults results = DeepSeekWebSearchEngine.builder()
                .apiKey(DEEPSEEK_API_KEY)
                .needContent(true)
                .timeout(Duration.ofSeconds(60))
                .build()
                .search(WebSearchRequest.builder()
                        .searchTerms("LangChain4j documentation")
                        .maxResults(1)
                        .build());

        assertThat(results.results()).isNotEmpty();
        assertThat(results.results().get(0).content()).isNotBlank();
    }

    @Test
    void search_async_should_return_results() throws Exception {
        WebSearchResults results = webSearchEngine
                .searchAsync(WebSearchRequest.from("LangChain4j"))
                .get(60, TimeUnit.SECONDS);

        assertThat(results.results()).isNotEmpty();
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
