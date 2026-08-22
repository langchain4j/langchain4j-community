package dev.langchain4j.community.web.search.serply;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.internal.UriUtils;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://serply.io">Serply</a> for performing web searches. <p>
 * Serply's search results also carry a "People Also Ask" section
 * ({@code related_questions} in the raw response), which is populated only
 * when present on the underlying search results page. It is exposed as-is
 * under the {@code relatedQuestions} key of {@link WebSearchResults#searchMetadata()}
 * instead of being mapped into dedicated fields, since its item shape is not documented.
 */
public class SerplyWebSearchEngine implements WebSearchEngine {

    private final String apiKey;
    private final SerplyClient client;

    private SerplyWebSearchEngine(Builder builder) {
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.client = new SerplyClient(
                apiKey,
                getOrDefault(builder.baseUrl, SerplyClient.defaultBaseUrl()),
                getOrDefault(builder.timeout, Duration.ofSeconds(10L)),
                builder.logRequests,
                builder.logResponses);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        SerplyWebSearchResponse response = client.search(webSearchRequest);
        return toWebSearchResults(response);
    }

    static WebSearchResults toWebSearchResults(SerplyWebSearchResponse response) {
        List<OrganicResult> results = getOrDefault(response.getResults(), List.of());

        Map<String, Object> searchMetadata = new HashMap<>();
        if (response.getRelatedQuestions() != null
                && !response.getRelatedQuestions().isEmpty()) {
            searchMetadata.put("relatedQuestions", response.getRelatedQuestions());
        }

        WebSearchInformationResult informationResult =
                WebSearchInformationResult.from(getOrDefault(response.getTotal(), (long) results.size()));

        return WebSearchResults.from(searchMetadata, informationResult, toWebSearchOrganicResults(results));
    }

    private static List<WebSearchOrganicResult> toWebSearchOrganicResults(List<OrganicResult> results) {
        return results.stream()
                .filter(result -> hasValue(result.getTitle()) && hasValue(result.getLink()))
                .map(SerplyWebSearchEngine::toWebSearchOrganicResult)
                .collect(Collectors.toList());
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(OrganicResult result) {
        Map<String, String> metadata = new HashMap<>();
        if (result.getPosition() != null) {
            metadata.put("position", String.valueOf(result.getPosition()));
        }
        return WebSearchOrganicResult.from(
                result.getTitle(),
                UriUtils.createUriSafely(result.getLink()),
                getOrDefault(result.getDescription(), ""),
                null,
                metadata);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        public SerplyWebSearchEngine build() {
            return new SerplyWebSearchEngine(this);
        }
    }
}
