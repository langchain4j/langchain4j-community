package dev.langchain4j.community.web.search.serply;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
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
 * <a href="https://serply.io">Serply</a> for performing web searches.
 * <p>
 * Pagination: {@link WebSearchRequest#startPage()} is translated into Serply's zero-based
 * {@code start} offset using {@link WebSearchRequest#maxResults()} (or Serply's default page
 * size of 10) as the page size, and is reported back as
 * {@link WebSearchInformationResult#pageNumber()}.
 * <p>
 * Total results: Serply's responses carry a top-level {@code total} field, but it is
 * {@code null} for regular web searches. When it is absent, {@link WebSearchInformationResult#totalResults()}
 * falls back to the number of results on the returned page (the same approach as the Tavily
 * engine) and {@link WebSearchInformationResult#metadata()} carries
 * {@code totalResultsEstimated=true} so callers can tell the two apart.
 * <p>
 * Serply's search results also carry a "People Also Ask" section
 * ({@code related_questions} in the raw response), which is populated only
 * when present on the underlying search results page. It is exposed as-is
 * under the {@code relatedQuestions} key of {@link WebSearchResults#searchMetadata()}
 * instead of being mapped into dedicated fields, since its item shape is not documented.
 */
public class SerplyWebSearchEngine implements WebSearchEngine {

    static final String TOTAL_RESULTS_ESTIMATED_KEY = "totalResultsEstimated";

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

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        SerplyWebSearchResponse response = client.search(webSearchRequest);
        return toWebSearchResults(response, webSearchRequest.startPage());
    }

    static WebSearchResults toWebSearchResults(SerplyWebSearchResponse response, Integer pageNumber) {
        List<SerplyOrganicResult> results = getOrDefault(response.getResults(), List.of());

        Map<String, Object> searchMetadata = new HashMap<>();
        if (response.getRelatedQuestions() != null
                && !response.getRelatedQuestions().isEmpty()) {
            searchMetadata.put("relatedQuestions", response.getRelatedQuestions());
        }

        return WebSearchResults.from(
                searchMetadata,
                toWebSearchInformationResult(response.getTotal(), results.size(), pageNumber),
                toWebSearchOrganicResults(results));
    }

    private static WebSearchInformationResult toWebSearchInformationResult(
            Long total, int resultCount, Integer pageNumber) {
        Map<String, Object> metadata = new HashMap<>();
        long totalResults;
        if (total != null) {
            totalResults = total;
        } else {
            totalResults = resultCount;
            metadata.put(TOTAL_RESULTS_ESTIMATED_KEY, true);
        }
        return WebSearchInformationResult.from(totalResults, getOrDefault(pageNumber, 1), metadata);
    }

    private static List<WebSearchOrganicResult> toWebSearchOrganicResults(List<SerplyOrganicResult> results) {
        return results.stream()
                .filter(result -> isNotNullOrBlank(result.getTitle()) && isNotNullOrBlank(result.getLink()))
                .map(SerplyWebSearchEngine::toWebSearchOrganicResult)
                .collect(Collectors.toList());
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(SerplyOrganicResult result) {
        Map<String, String> metadata = new HashMap<>();
        Integer position = getOrDefault(result.getPosition(), result.getRealPosition());
        if (position != null) {
            metadata.put("position", String.valueOf(position));
        }
        // A missing description is passed through as null: the core contract tolerates a null
        // snippet, and an empty string would only hide the fact that Serply sent none.
        return WebSearchOrganicResult.from(
                result.getTitle(), UriUtils.createUriSafely(result.getLink()), result.getDescription(), null, metadata);
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
