package dev.langchain4j.community.web.search.olostep;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents Olostep Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://docs.olostep.com/features/search">here</a>.
 * <br>
 * Each result carries the description returned by Olostep in {@link WebSearchOrganicResult#snippet()}.
 * <br>
 * When {@link Builder#scrapeContent(Boolean)} is set to {@code true}, Olostep also scrapes each result and its
 * content appears as Markdown in the {@link WebSearchOrganicResult#content()} field. Scraping uses additional
 * credits and takes longer, so the default timeout is raised to 90 seconds in that case. Olostep allows the
 * scrape phase 25 seconds by default, but a whole search-and-scrape request can take close to a minute under
 * load. A result whose page could not be scraped in time keeps {@link WebSearchOrganicResult#content()}
 * {@code null}.
 * <br>
 * {@link WebSearchRequest#maxResults()} is sent as the {@code limit}, which Olostep accepts between 1 and 25,
 * so values outside that range are clamped to it. When it is not set, Olostep returns up to 12 results.
 */
public class OlostepWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.olostep.com";
    private static final Duration DEFAULT_TIMEOUT = ofSeconds(30);
    private static final Duration DEFAULT_SCRAPE_TIMEOUT = ofSeconds(90);
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 25;

    private final OlostepClient client;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;
    private final Boolean scrapeContent;

    private OlostepWebSearchEngine(Builder builder) {
        this.scrapeContent = builder.scrapeContent;
        this.client = new OlostepClient(
                builder.httpClientBuilder,
                getOrDefault(builder.baseUrl, DEFAULT_BASE_URL),
                ensureNotBlank(builder.apiKey, "apiKey"),
                getOrDefault(
                        builder.timeout, Boolean.TRUE.equals(scrapeContent) ? DEFAULT_SCRAPE_TIMEOUT : DEFAULT_TIMEOUT),
                builder.logRequests,
                builder.logResponses);
        this.includeDomains = isNullOrEmpty(builder.includeDomains) ? null : copy(builder.includeDomains);
        this.excludeDomains = isNullOrEmpty(builder.excludeDomains) ? null : copy(builder.excludeDomains);
    }

    /**
     * Creates a new builder for {@link OlostepWebSearchEngine}.
     *
     * @return {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an {@link OlostepWebSearchEngine} with the given API key and default settings.
     *
     * @param apiKey Olostep API key
     * @return {@link OlostepWebSearchEngine}
     */
    public static OlostepWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        ensureNotNull(webSearchRequest, "webSearchRequest");
        return toWebSearchResults(client.search(toOlostepRequest(webSearchRequest)));
    }

    /**
     * Non-blocking counterpart of {@link #search(WebSearchRequest)}.
     * Cancelling the returned future aborts the in-flight call (best-effort).
     */
    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        ensureNotNull(webSearchRequest, "webSearchRequest");
        CompletableFuture<OlostepSearchResponse> response = client.searchAsync(toOlostepRequest(webSearchRequest));
        CompletableFuture<WebSearchResults> result = response.thenApply(OlostepWebSearchEngine::toWebSearchResults);
        propagateCancellation(result, response);
        return result;
    }

    private OlostepSearchRequest toOlostepRequest(WebSearchRequest webSearchRequest) {
        return OlostepSearchRequest.builder()
                .query(webSearchRequest.searchTerms())
                .limit(limit(webSearchRequest.maxResults()))
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .scrapeContent(scrapeContent)
                .build();
    }

    private static Integer limit(Integer maxResults) {
        return maxResults == null ? null : Math.min(Math.max(maxResults, MIN_LIMIT), MAX_LIMIT);
    }

    static WebSearchResults toWebSearchResults(OlostepSearchResponse response) {
        OlostepSearchResponse.Result result = response == null ? null : response.getResult();
        List<OlostepSearchResult> links = result == null ? null : result.getLinks();
        List<WebSearchOrganicResult> results = new ArrayList<>();
        if (links != null) {
            for (OlostepSearchResult link : links) {
                if (isNullOrBlank(link.getTitle())) {
                    continue;
                }
                URI url = UriUtils.createUriSafely(link.getUrl());
                if (url == null) {
                    continue;
                }
                // Olostep only returns markdown_content when scraping was requested, and null when a page
                // could not be scraped; the core contract represents absent content as null.
                String content = isNullOrBlank(link.getMarkdownContent()) ? null : link.getMarkdownContent();
                results.add(WebSearchOrganicResult.from(link.getTitle(), url, link.getDescription(), content));
            }
        }
        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    /**
     * Builder for new instances of {@link OlostepWebSearchEngine}.
     */
    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private Boolean scrapeContent;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        Builder() {}

        /**
         * @param baseUrl base URL of the Olostep API, defaults to {@code https://api.olostep.com}.
         *                The {@code /v1/searches} path is appended to it.
         * @return {@link Builder}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param apiKey Olostep API key (required)
         * @return {@link Builder}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param timeout connect and read timeout, defaults to 30 seconds, or 90 seconds when content is scraped
         * @return {@link Builder}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param includeDomains domains to restrict results to, as bare hosts, e.g. {@code "docs.langchain4j.dev"}
         * @return {@link Builder}
         */
        public Builder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        /**
         * @param excludeDomains domains to exclude from results, as bare hosts, e.g. {@code "pinterest.com"}
         * @return {@link Builder}
         */
        public Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        /**
         * @param scrapeContent whether Olostep should scrape each result and return its content as Markdown in
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
         * Creates a new instance of {@link OlostepWebSearchEngine}.
         *
         * @return the new instance
         */
        public OlostepWebSearchEngine build() {
            return new OlostepWebSearchEngine(this);
        }
    }
}
