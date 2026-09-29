package dev.langchain4j.community.web.search.firecrawl;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.exception.LangChain4jException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents Firecrawl Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://docs.firecrawl.dev/features/search?utm_source=langchain4j&utm_medium=integration">here</a>.
 * <br>
 * Each result carries the query-relevant highlights returned by Firecrawl in {@link WebSearchOrganicResult#snippet()},
 * and its rank in the {@code position} metadata entry.
 * <br>
 * When {@link Builder#scrapeContent(Boolean)} is set to {@code true}, Firecrawl also scrapes each result and its
 * content appears as Markdown in the {@link WebSearchOrganicResult#content()} field. Scraping uses additional
 * credits and takes longer, so the default timeout is raised to 60 seconds in that case.
 * <br>
 * {@link WebSearchRequest#maxResults()} is sent as the {@code limit}, which Firecrawl accepts between 1 and 100.
 */
public class FirecrawlWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.firecrawl.dev/v2/";
    private static final Duration DEFAULT_TIMEOUT = ofSeconds(10);
    private static final Duration DEFAULT_SCRAPE_TIMEOUT = ofSeconds(60);
    private static final List<String> SOURCES = List.of("web");
    private static final String ORIGIN = "langchain4j";

    private final FirecrawlClient client;
    private final String location;
    private final String tbs;
    private final Boolean scrapeContent;

    private FirecrawlWebSearchEngine(Builder builder) {
        this.scrapeContent = builder.scrapeContent;
        this.client = new FirecrawlClient(
                builder.httpClientBuilder,
                getOrDefault(builder.baseUrl, DEFAULT_BASE_URL),
                ensureNotBlank(builder.apiKey, "apiKey"),
                getOrDefault(
                        builder.timeout, Boolean.TRUE.equals(scrapeContent) ? DEFAULT_SCRAPE_TIMEOUT : DEFAULT_TIMEOUT),
                builder.logRequests,
                builder.logResponses);
        this.location = builder.location;
        this.tbs = builder.tbs;
    }

    /**
     * Creates a new builder for {@link FirecrawlWebSearchEngine}.
     *
     * @return {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a {@link FirecrawlWebSearchEngine} with the given API key and default settings.
     *
     * @param apiKey Firecrawl API key
     * @return {@link FirecrawlWebSearchEngine}
     */
    public static FirecrawlWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        return toWebSearchResults(client.search(toFirecrawlRequest(webSearchRequest)));
    }

    /**
     * Non-blocking counterpart of {@link #search(WebSearchRequest)}.
     * Cancelling the returned future aborts the in-flight call (best-effort).
     */
    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        CompletableFuture<FirecrawlSearchResponse> searchFuture =
                client.searchAsync(toFirecrawlRequest(webSearchRequest));
        CompletableFuture<WebSearchResults> result =
                searchFuture.thenApply(FirecrawlWebSearchEngine::toWebSearchResults);
        propagateCancellation(result, searchFuture);
        return result;
    }

    private FirecrawlSearchRequest toFirecrawlRequest(WebSearchRequest webSearchRequest) {
        return FirecrawlSearchRequest.builder()
                .query(webSearchRequest.searchTerms())
                .limit(webSearchRequest.maxResults())
                .sources(SOURCES)
                .location(location)
                .tbs(tbs)
                .scrapeContent(scrapeContent)
                .origin(ORIGIN)
                .build();
    }

    static WebSearchResults toWebSearchResults(FirecrawlSearchResponse response) {
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            String error = response == null ? null : response.getError();
            throw new LangChain4jException("Firecrawl search failed: " + getOrDefault(error, "unknown error"));
        }
        List<WebSearchOrganicResult> results = new ArrayList<>();
        if (response.getData() != null && response.getData().getWeb() != null) {
            for (FirecrawlSearchResult result : response.getData().getWeb()) {
                if (isNullOrBlank(result.getTitle())) {
                    continue;
                }
                URI url = UriUtils.createUriSafely(result.getUrl());
                if (url == null) {
                    continue;
                }
                results.add(WebSearchOrganicResult.from(
                        result.getTitle(), url, result.getDescription(), result.getMarkdown(), metadata(result)));
            }
        }
        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    private static Map<String, String> metadata(FirecrawlSearchResult result) {
        return result.getPosition() == null
                ? Collections.emptyMap()
                : Collections.singletonMap("position", String.valueOf(result.getPosition()));
    }

    /**
     * Builder for new instances of {@link FirecrawlWebSearchEngine}.
     */
    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private String location;
        private String tbs;
        private Boolean scrapeContent;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        Builder() {}

        /**
         * @param baseUrl base URL of the Firecrawl API, defaults to {@code https://api.firecrawl.dev/v2/}
         * @return {@link Builder}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey Firecrawl API key (required)
         * @return {@link Builder}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param timeout connect and read timeout, defaults to 10 seconds, or 60 seconds when content is scraped
         * @return {@link Builder}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param location location used to localize results, e.g. {@code "Germany"}
         * @return {@link Builder}
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * @param tbs time-based filter, e.g. {@code "qdr:w"} for results from the past week
         * @return {@link Builder}
         */
        public Builder tbs(String tbs) {
            this.tbs = tbs;
            return this;
        }

        /**
         * @param scrapeContent whether Firecrawl should scrape each result and return its content as Markdown in
         *                      {@link WebSearchOrganicResult#content()}. Uses additional credits.
         * @return {@link Builder}
         */
        public Builder scrapeContent(Boolean scrapeContent) {
            this.scrapeContent = scrapeContent;
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
         * Creates a new instance of {@link FirecrawlWebSearchEngine}.
         *
         * @return the new instance
         */
        public FirecrawlWebSearchEngine build() {
            return new FirecrawlWebSearchEngine(this);
        }

        @Override
        public String toString() {
            return "FirecrawlWebSearchEngine.Builder(baseUrl=" + baseUrl + ", apiKey="
                    + (apiKey == null ? null : "********") + ", timeout=" + timeout + ", location=" + location
                    + ", tbs=" + tbs + ", scrapeContent=" + scrapeContent + ", logRequests=" + logRequests
                    + ", logResponses=" + logResponses + ")";
        }
    }
}
