package dev.langchain4j.community.web.search.searxng;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Testcontainers
class SearXNGWebSearchEngineIT extends WebSearchEngineIT {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> searxng = new GenericContainer<>(DockerImageName.parse("searxng/searxng:latest"))
            .withExposedPorts(8080)
            .withCopyFileToContainer(MountableFile.forClasspathResource("settings.yml"), "/usr/local/searxng/searx/settings.yml")
            .waitingFor(Wait.forLogMessage(".*spawned uWSGI worker.*\\n", 1));

    @Override
    protected WebSearchEngine searchEngine() {
        return SearXNGWebSearchEngine.builder().baseUrl("http://" + searxng.getHost() + ":" + searxng.getMappedPort(8080)).build();
    }

    @BeforeAll
    static void startContainers() {
        searxng.start();
    }

    @AfterAll
    static void stopContainers() {
        searxng.stop();
        searxng.close();
    }

    private static String contentForComparison(WebSearchOrganicResult webSearchOrganicResult) {
        return webSearchOrganicResult.toString();
    }

    private static String metadataForComparison(WebSearchOrganicResult webSearchOrganicResult, String key) {
        return webSearchOrganicResult.metadata().get(key);
    }

    private static String engine(WebSearchOrganicResult webSearchOrganicResult) {
        return metadataForComparison(webSearchOrganicResult, "engine");
    }

    @Test
    void should_search_with_start_page() {
        // given
        final String searchTerms = "What is Artificial Intelligence?";
        WebSearchRequest request1 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .build();

        WebSearchRequest request2 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .startPage(2)
                .build();

        WebSearchRequest request3 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .startPage(3)
                .build();
        // when
        WebSearchResults webSearchResults1 = searchEngine().search(request1);
        WebSearchResults webSearchResults2 = searchEngine().search(request2);
        WebSearchResults webSearchResults3 = searchEngine().search(request3);

        // then
        assertNotEquals(contentForComparison(webSearchResults1.results().get(0)), contentForComparison(webSearchResults2.results().get(0)));
        assertNotEquals(contentForComparison(webSearchResults1.results().get(0)), contentForComparison(webSearchResults3.results().get(0)));
        assertNotEquals(contentForComparison(webSearchResults2.results().get(0)), contentForComparison(webSearchResults3.results().get(0)));

        assertNotEquals(contentForComparison(webSearchResults1.results().get(1)), contentForComparison(webSearchResults2.results().get(1)));
        assertNotEquals(contentForComparison(webSearchResults1.results().get(1)), contentForComparison(webSearchResults3.results().get(1)));
        assertNotEquals(contentForComparison(webSearchResults2.results().get(1)), contentForComparison(webSearchResults3.results().get(1)));
    }

    @Test
    void should_search_with_language() {
        // given
        // should be generic across many languages
        final String searchTerms = "AI";
        WebSearchRequest request1 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .language("en-US")
                .build();

        WebSearchRequest request2 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .language("fr")
                .build();

        // when
        WebSearchResults webSearchResults1 = searchEngine().search(request1);
        WebSearchResults webSearchResults2 = searchEngine().search(request2);

        // then
        assertNotEquals(contentForComparison(webSearchResults1.results().get(0)), contentForComparison(webSearchResults2.results().get(0)));
        assertNotEquals(contentForComparison(webSearchResults1.results().get(1)), contentForComparison(webSearchResults2.results().get(1)));
    }

    @Test
    void should_search_with_additional_params() {
        // given
        // Choose three very different search engines
        final String searchTerms = "qqq stock quote";
        final Map<String, Object> additionalParams1 = new HashMap<>();
        additionalParams1.put("engines", "google");
        WebSearchRequest request1 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .additionalParams(additionalParams1)
                .build();

        final Map<String, Object> additionalParams2 = new HashMap<>();
        additionalParams2.put("engines", "bing");
        WebSearchRequest request2 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .additionalParams(additionalParams2)
                .build();

        final Map<String, Object> additionalParams3 = new HashMap<>();
        additionalParams3.put("engines", "yahoo");
        WebSearchRequest request3 = WebSearchRequest.builder()
                .searchTerms(searchTerms)
                .additionalParams(additionalParams3)
                .build();
        // when
        WebSearchResults webSearchResults1 = searchEngine().search(request1);
        WebSearchResults webSearchResults2 = searchEngine().search(request2);
        WebSearchResults webSearchResults3 = searchEngine().search(request3);

        // then
        for (final WebSearchOrganicResult result : webSearchResults1.results()) {
            assertEquals("google", engine(result));

        }
        for (final WebSearchOrganicResult result : webSearchResults2.results()) {
            assertEquals("bing", engine(result));

        }
        for (final WebSearchOrganicResult result : webSearchResults3.results()) {
            assertEquals("yahoo", engine(result));

        }
    }
}
