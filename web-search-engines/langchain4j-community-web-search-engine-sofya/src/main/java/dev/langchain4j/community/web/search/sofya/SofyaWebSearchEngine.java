package dev.langchain4j.community.web.search.sofya;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents the Sofya Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://sofya.co/docs">here</a>.
 * <br>
 * Each result carries its search snippet in {@link WebSearchOrganicResult#snippet()}. By default
 * ({@link SofyaSearchDepth#SNIPPETS}) that is all, and {@code content()} is {@code null}. With
 * {@link Builder#searchDepth(SofyaSearchDepth)} set to {@link SofyaSearchDepth#BASIC}, Sofya also fetches each
 * result page in the same call, and its text appears as Markdown in {@link WebSearchOrganicResult#content()}.
 * That takes a few seconds longer and uses additional credits. A page that could not be read in time keeps
 * {@code content()} at {@code null}.
 * <br>
 * When the publication date of a result is known, it is in the {@code published_date} metadata entry
 * ({@code YYYY-MM-DD}).
 * <br>
 * When {@link Builder#includeAnswer(Boolean)} is set to {@code true}, Sofya also writes a short answer from the
 * results. It is prepended as a result whose {@code result_type} metadata entry is {@code answer}, so the model
 * reads it first, and it is also available under the {@code answer} key of {@link WebSearchResults#searchMetadata()}.
 * This uses additional credits.
 * <br>
 * {@link WebSearchRequest#maxResults()} is sent as {@code max_results}, which Sofya accepts between 1 and 20
 * (values outside are clamped). Other {@link WebSearchRequest} fields are not supported and are ignored.
 */
public class SofyaWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://sofya.co/";
    private static final Duration DEFAULT_TIMEOUT = ofSeconds(30);
    private static final SofyaSearchDepth DEFAULT_SEARCH_DEPTH = SofyaSearchDepth.SNIPPETS;
    private static final String ANSWER_TITLE = "Sofya answer";
    private static final URI ANSWER_URL = URI.create("https://sofya.co/");

    private final SofyaClient client;
    private final SofyaSearchDepth searchDepth;
    private final Boolean includeAnswer;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;
    private final SofyaSearchTopic topic;
    private final String freshness;

    private SofyaWebSearchEngine(Builder builder) {
        this.client = new SofyaClient(
                builder.httpClientBuilder,
                getOrDefault(builder.baseUrl, DEFAULT_BASE_URL),
                ensureNotBlank(builder.apiKey, "apiKey"),
                getOrDefault(builder.timeout, DEFAULT_TIMEOUT),
                builder.logRequests,
                builder.logResponses);
        this.searchDepth = getOrDefault(builder.searchDepth, DEFAULT_SEARCH_DEPTH);
        this.includeAnswer = builder.includeAnswer;
        this.includeDomains = builder.includeDomains;
        this.excludeDomains = builder.excludeDomains;
        this.topic = builder.topic;
        this.freshness = builder.freshness;
    }

    /**
     * Creates a new builder for {@link SofyaWebSearchEngine}.
     *
     * @return {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a {@link SofyaWebSearchEngine} with the given API key and default settings.
     *
     * @param apiKey Sofya API key
     * @return {@link SofyaWebSearchEngine}
     */
    public static SofyaWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        return toWebSearchResults(client.search(toSofyaRequest(webSearchRequest)));
    }

    /**
     * Non-blocking counterpart of {@link #search(WebSearchRequest)}.
     * Cancelling the returned future aborts the in-flight call (best-effort).
     */
    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        CompletableFuture<SofyaSearchResponse> searchFuture = client.searchAsync(toSofyaRequest(webSearchRequest));
        CompletableFuture<WebSearchResults> result = searchFuture.thenApply(SofyaWebSearchEngine::toWebSearchResults);
        propagateCancellation(result, searchFuture);
        return result;
    }

    private SofyaSearchRequest toSofyaRequest(WebSearchRequest webSearchRequest) {
        return SofyaSearchRequest.builder()
                .query(webSearchRequest.searchTerms())
                .maxResults(webSearchRequest.maxResults())
                .searchDepth(searchDepth)
                .includeAnswer(includeAnswer)
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .topic(topic)
                .freshness(freshness)
                .build();
    }

    static WebSearchResults toWebSearchResults(SofyaSearchResponse response) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        if (response.getResults() != null) {
            for (SofyaSearchResult result : response.getResults()) {
                if (isNullOrBlank(result.getTitle())) {
                    continue;
                }
                URI url = UriUtils.createUriSafely(result.getUrl());
                if (url == null) {
                    continue;
                }
                results.add(WebSearchOrganicResult.from(
                        result.getTitle(), url, snippet(result), content(result), metadata(result)));
            }
        }
        WebSearchInformationResult information = WebSearchInformationResult.from((long) results.size());

        Map<String, Object> searchMetadata = new HashMap<>();
        putIfNotBlank(searchMetadata, "query", response.getQuery());
        putIfNotBlank(searchMetadata, "altered_query", response.getAlteredQuery());
        putIfNotBlank(searchMetadata, "relaxed_query", response.getRelaxedQuery());

        if (!isNullOrBlank(response.getAnswer())) {
            searchMetadata.put("answer", response.getAnswer());
            results.add(
                    0,
                    WebSearchOrganicResult.from(
                            ANSWER_TITLE,
                            ANSWER_URL,
                            response.getAnswer(),
                            null,
                            Collections.singletonMap("result_type", "answer")));
        }
        return WebSearchResults.from(searchMetadata, information, results);
    }

    private static String snippet(SofyaSearchResult result) {
        if (!isNullOrBlank(result.getDescription())) {
            return result.getDescription();
        }
        // without a description, the content of a result that was not fetched is its snippet
        return Boolean.TRUE.equals(result.getFetched()) ? null : result.getContent();
    }

    private static String content(SofyaSearchResult result) {
        return Boolean.TRUE.equals(result.getFetched()) && !isNullOrBlank(result.getContent())
                ? result.getContent()
                : null;
    }

    private static Map<String, String> metadata(SofyaSearchResult result) {
        return isNullOrBlank(result.getPublishedDate())
                ? Collections.emptyMap()
                : Collections.singletonMap("published_date", result.getPublishedDate());
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (!isNullOrBlank(value)) {
            map.put(key, value);
        }
    }

    /**
     * Builder for new instances of {@link SofyaWebSearchEngine}.
     */
    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private SofyaSearchDepth searchDepth;
        private Boolean includeAnswer;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private SofyaSearchTopic topic;
        private String freshness;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        Builder() {}

        /**
         * @param baseUrl base URL of the Sofya API, defaults to {@code https://sofya.co/}
         * @return {@link Builder}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey Sofya API key (required)
         * @return {@link Builder}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param timeout connect and read timeout, defaults to 30 seconds, which leaves room for page fetching
         *                and an answer
         * @return {@link Builder}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param searchDepth {@link SofyaSearchDepth#SNIPPETS} (the default) for snippets only, or
         *                    {@link SofyaSearchDepth#BASIC} to also return each page's text as Markdown in
         *                    {@link WebSearchOrganicResult#content()}. {@code BASIC} uses additional credits.
         * @return {@link Builder}
         */
        public Builder searchDepth(SofyaSearchDepth searchDepth) {
            this.searchDepth = searchDepth;
            return this;
        }

        /**
         * @param includeAnswer whether Sofya should also write a short answer from the results, prepended as the
         *                      first result. Uses additional credits.
         * @return {@link Builder}
         */
        public Builder includeAnswer(Boolean includeAnswer) {
            this.includeAnswer = includeAnswer;
            return this;
        }

        /**
         * @param includeDomains only return results from these domains (at most 10), as bare hostnames such as
         *                       {@code "github.com"}
         * @return {@link Builder}
         */
        public Builder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        /**
         * @param excludeDomains never return results from these domains (at most 10), as bare hostnames such as
         *                       {@code "pinterest.com"}
         * @return {@link Builder}
         */
        public Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        /**
         * @param topic {@link SofyaSearchTopic#GENERAL} (the API's default) or {@link SofyaSearchTopic#NEWS}
         * @return {@link Builder}
         */
        public Builder topic(SofyaSearchTopic topic) {
            this.topic = topic;
            return this;
        }

        /**
         * @param freshness only return recent results: {@code "day"}, {@code "week"}, {@code "month"},
         *                  {@code "year"}, or a date range such as {@code "2026-01-01:2026-03-31"}
         * @return {@link Builder}
         */
        public Builder freshness(String freshness) {
            this.freshness = freshness;
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
         * Creates a new instance of {@link SofyaWebSearchEngine}.
         *
         * @return the new instance
         */
        public SofyaWebSearchEngine build() {
            return new SofyaWebSearchEngine(this);
        }

        @Override
        public String toString() {
            return "SofyaWebSearchEngine.Builder(baseUrl=" + baseUrl + ", apiKey="
                    + (apiKey == null ? null : "********") + ", timeout=" + timeout + ", searchDepth=" + searchDepth
                    + ", includeAnswer=" + includeAnswer + ", includeDomains=" + includeDomains + ", excludeDomains="
                    + excludeDomains + ", topic=" + topic + ", freshness=" + freshness + ", logRequests="
                    + logRequests + ", logResponses=" + logResponses + ")";
        }
    }
}
