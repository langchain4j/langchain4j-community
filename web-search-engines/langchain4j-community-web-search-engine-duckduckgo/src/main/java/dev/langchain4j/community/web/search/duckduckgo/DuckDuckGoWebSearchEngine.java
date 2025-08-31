package dev.langchain4j.community.web.search.duckduckgo;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Represents a DuckDuckGo web search engine implementation.
 * Uses HTML parsing to extract search results from DuckDuckGo's search page.
 */
public class DuckDuckGoWebSearchEngine implements WebSearchEngine {

    private final DuckDuckGoClient client;

    private DuckDuckGoWebSearchEngine(Builder builder) {
        this.client = new DuckDuckGoClient(
                getOrDefault(builder.duration, Duration.ofSeconds(10L)), builder.logRequests, builder.logResponses);
    }

    /**
     * Creates a new builder for DuckDuckGoWebSearchEngine.
     *
     * @return {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        List<DuckDuckGoSearchResult> results = client.search(webSearchRequest);

        List<WebSearchOrganicResult> organicResults = results.stream()
                .filter(DuckDuckGoWebSearchEngine::includeResult)
                .map(DuckDuckGoWebSearchEngine::toWebSearchOrganicResult)
                .limit(maxResults(webSearchRequest))
                .collect(Collectors.toList());

        return WebSearchResults.from(WebSearchInformationResult.from((long) organicResults.size()), organicResults);
    }

    /**
     * Performs asynchronous search.
     *
     * @param webSearchRequest the search request
     * @return CompletableFuture containing search results
     */
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        return client.searchAsync(webSearchRequest).thenApply(results -> {
            List<WebSearchOrganicResult> organicResults = results.stream()
                    .filter(DuckDuckGoWebSearchEngine::includeResult)
                    .map(DuckDuckGoWebSearchEngine::toWebSearchOrganicResult)
                    .limit(maxResults(webSearchRequest))
                    .collect(Collectors.toList());

            return WebSearchResults.from(WebSearchInformationResult.from((long) organicResults.size()), organicResults);
        });
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(DuckDuckGoSearchResult result) {
        return WebSearchOrganicResult.from(result.getTitle(), makeURI(result.getUrl()), result.getSnippet(), null);
    }

    protected static URI makeURI(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("urlString can not be null or blank");
        }
        return URI.create(urlString.replaceAll("\\s+", "%20"));
    }

    private static boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean includeResult(DuckDuckGoSearchResult result) {
        return hasValue(result.getTitle()) && hasValue(result.getUrl()) && hasValue(result.getSnippet());
    }

    private static int maxResults(WebSearchRequest webSearchRequest) {
        return webSearchRequest.maxResults() != null ? webSearchRequest.maxResults() : Integer.MAX_VALUE;
    }

    /**
     * Builder for new instances of {@link DuckDuckGoWebSearchEngine}.
     */
    public static class Builder {
        private Duration duration;
        private boolean logRequests;
        private boolean logResponses;

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public DuckDuckGoWebSearchEngine build() {
            return new DuckDuckGoWebSearchEngine(this);
        }
    }
}
