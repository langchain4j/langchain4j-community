package dev.langchain4j.community.web.search.keenable;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.UriUtils;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents Keenable Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://docs.keenable.ai/api-reference/search">here</a>.
 * <br>
 * The API key is optional. Without one, requests go to the keyless public endpoint
 * ({@code POST /v1/search/public}), which Keenable rate-limits per IP address. With a key, requests go to
 * {@code POST /v1/search} with the {@code X-API-Key} header. Both send the {@code X-Keenable-Title} header,
 * which the public endpoint requires (see {@link Builder#appTitle(String)}).
 * <br>
 * Each result carries the query-relevant page text returned by Keenable in {@link WebSearchOrganicResult#snippet()},
 * its rank in the {@code position} metadata entry, and, when Keenable knows them, {@code published_at} and
 * {@code acquired_at} (ISO 8601, UTC).
 * <br>
 * {@link WebSearchRequest#maxResults()} is sent as {@code max_results}, which Keenable accepts between 1 and 50;
 * larger values are capped at 50. {@link WebSearchRequest#safeSearch()} is not mapped: {@code WebSearchRequest}
 * turns it on by default, while Keenable's {@code safe_search} defaults to off and is not available in every mode.
 */
public class KeenableWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.keenable.ai/v1/";
    private static final String DEFAULT_APP_TITLE = "LangChain4j";
    private static final Duration DEFAULT_TIMEOUT = ofSeconds(30);
    private static final int MAX_RESULTS_LIMIT = 50;

    private final KeenableClient client;
    private final String mode;
    private final Integer snippetMaxLength;
    private final List<String> excludeDomains;

    private KeenableWebSearchEngine(Builder builder) {
        String appTitle = getOrDefault(builder.appTitle, DEFAULT_APP_TITLE);
        ensureNotBlank(appTitle, "appTitle");
        // the title is sent as a header value, so it must not carry line breaks or other control characters
        if (appTitle.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new IllegalArgumentException("appTitle must not contain control characters");
        }
        this.client = new KeenableClient(
                builder.httpClientBuilder,
                getOrDefault(builder.baseUrl, DEFAULT_BASE_URL),
                isNullOrBlank(builder.apiKey) ? null : builder.apiKey,
                appTitle,
                getOrDefault(builder.timeout, DEFAULT_TIMEOUT),
                builder.logRequests,
                builder.logResponses);
        this.mode = builder.mode;
        this.snippetMaxLength = builder.snippetMaxLength;
        this.excludeDomains = builder.excludeDomains == null ? null : List.copyOf(builder.excludeDomains);
    }

    /**
     * Creates a new builder for {@link KeenableWebSearchEngine}.
     *
     * @return {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a {@link KeenableWebSearchEngine} with the given API key and default settings.
     *
     * @param apiKey Keenable API key
     * @return {@link KeenableWebSearchEngine}
     */
    public static KeenableWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(ensureNotBlank(apiKey, "apiKey")).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        return toWebSearchResults(client.search(toKeenableRequest(webSearchRequest)));
    }

    /**
     * Non-blocking counterpart of {@link #search(WebSearchRequest)}.
     * Cancelling the returned future aborts the in-flight call (best-effort).
     */
    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        CompletableFuture<KeenableSearchResponse> searchFuture =
                client.searchAsync(toKeenableRequest(webSearchRequest));
        CompletableFuture<WebSearchResults> result =
                searchFuture.thenApply(KeenableWebSearchEngine::toWebSearchResults);
        propagateCancellation(result, searchFuture);
        return result;
    }

    private KeenableSearchRequest toKeenableRequest(WebSearchRequest webSearchRequest) {
        Integer maxResults = webSearchRequest.maxResults();
        return KeenableSearchRequest.builder()
                .query(webSearchRequest.searchTerms())
                .mode(mode)
                .maxResults(maxResults == null ? null : Math.min(maxResults, MAX_RESULTS_LIMIT))
                .snippetMaxLength(snippetMaxLength)
                .excludeDomains(excludeDomains == null || excludeDomains.isEmpty() ? null : excludeDomains)
                .build();
    }

    static WebSearchResults toWebSearchResults(KeenableSearchResponse response) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        if (response != null && response.getResults() != null) {
            for (KeenableSearchResult result : response.getResults()) {
                URI url = UriUtils.createUriSafely(result.getUrl());
                if (url == null || isNullOrBlank(result.getTitle())) {
                    continue;
                }
                results.add(WebSearchOrganicResult.from(
                        result.getTitle(), url, result.getSnippet(), null, metadata(result, results.size() + 1)));
            }
        }
        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    private static Map<String, String> metadata(KeenableSearchResult result, int position) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("position", String.valueOf(position));
        if (!isNullOrBlank(result.getPublishedAt())) {
            metadata.put("published_at", result.getPublishedAt());
        }
        if (!isNullOrBlank(result.getAcquiredAt())) {
            metadata.put("acquired_at", result.getAcquiredAt());
        }
        return metadata;
    }

    /**
     * Builder for new instances of {@link KeenableWebSearchEngine}.
     */
    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private String appTitle;
        private Duration timeout;
        private String mode;
        private Integer snippetMaxLength;
        private List<String> excludeDomains;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        Builder() {}

        /**
         * @param baseUrl base URL of the Keenable API, defaults to {@code https://api.keenable.ai/v1/}
         * @return {@link Builder}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey Keenable API key (optional). Without it, the keyless public endpoint is used,
         *               which Keenable rate-limits per IP address.
         * @return {@link Builder}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param appTitle your application's name, sent as the {@code X-Keenable-Title} header (required by the
         *                 keyless endpoint), defaults to {@code "LangChain4j"}
         * @return {@link Builder}
         */
        public Builder appTitle(String appTitle) {
            this.appTitle = appTitle;
            return this;
        }

        /**
         * @param timeout connect and read timeout, defaults to 30 seconds
         * @return {@link Builder}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param mode search mode, {@code "pro"} (Keenable's default, deeper retrieval) or {@code "realtime"}
         *             (fastest response). Left out when {@code null}.
         * @return {@link Builder}
         */
        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        /**
         * @param snippetMaxLength cap on the characters of page text in each snippet, from 180 to 10000.
         *                         Left out when {@code null}.
         * @return {@link Builder}
         */
        public Builder snippetMaxLength(Integer snippetMaxLength) {
            this.snippetMaxLength = snippetMaxLength;
            return this;
        }

        /**
         * @param excludeDomains hosts never to return results from, e.g. {@code "pinterest.com"}
         *                       (subdomains are excluded too)
         * @return {@link Builder}
         */
        public Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        /**
         * @param httpClientBuilder custom {@link HttpClientBuilder}, defaults to the one found on the classpath
         * @return {@link Builder}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * @param logRequests whether to log HTTP requests
         * @return {@link Builder}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses whether to log HTTP responses
         * @return {@link Builder}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Creates a new instance of {@link KeenableWebSearchEngine}.
         *
         * @return the new instance
         */
        public KeenableWebSearchEngine build() {
            return new KeenableWebSearchEngine(this);
        }

        @Override
        public String toString() {
            return "KeenableWebSearchEngine.Builder(baseUrl=" + baseUrl + ", apiKey="
                    + (apiKey == null ? null : "********") + ", appTitle=" + appTitle + ", timeout=" + timeout
                    + ", mode=" + mode + ", snippetMaxLength=" + snippetMaxLength + ", excludeDomains="
                    + excludeDomains + ", logRequests=" + logRequests + ", logResponses=" + logResponses + ")";
        }
    }
}
