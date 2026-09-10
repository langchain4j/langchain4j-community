package dev.langchain4j.community.web.search.serply;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.internal.RetryUtils.RetryPolicy;
import dev.langchain4j.web.search.WebSearchRequest;
import java.time.Duration;

class SerplyClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.serply.io";

    /**
     * Number of results Serply returns per page when {@code num} is not sent.
     * Used to translate {@link WebSearchRequest#startPage()} into Serply's {@code start} offset.
     */
    static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final RetryPolicy retryPolicy;

    SerplyClient(String apiKey, String baseUrl, Duration timeout, boolean logRequests, boolean logResponses) {
        this(apiKey, baseUrl, buildHttpClient(timeout, logRequests, logResponses), defaultRetryPolicy());
    }

    SerplyClient(String apiKey, String baseUrl, HttpClient httpClient, RetryPolicy retryPolicy) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.retryPolicy = retryPolicy;
    }

    private static HttpClient buildHttpClient(Duration timeout, boolean logRequests, boolean logResponses) {
        HttpClientBuilder builder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
        HttpClient client = builder.build();
        return (logRequests || logResponses) ? new LoggingHttpClient(client, logRequests, logResponses) : client;
    }

    private static RetryPolicy defaultRetryPolicy() {
        return RetryUtils.retryPolicyBuilder().maxRetries(2).delayMillis(1000).build();
    }

    /**
     * Executes the search. Only transient failures are retried: HTTP 429/5xx (and transport errors)
     * go through the retry policy, while 401/403 and other 4xx responses fail fast with a typed
     * {@link dev.langchain4j.exception.NonRetriableException} carrying the status code and body.
     */
    SerplyWebSearchResponse search(WebSearchRequest webSearchRequest) {
        HttpRequest request = buildRequest(webSearchRequest);
        return retryPolicy.withRetry(() -> {
            SuccessfulHttpResponse response;
            try {
                response = httpClient.execute(request);
            } catch (HttpException e) {
                throw toException(e);
            }
            return parse(response.body());
        });
    }

    HttpRequest buildRequest(WebSearchRequest webSearchRequest) {
        HttpRequest.Builder requestBuilder = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "v1/search")
                .addQueryParam("q", webSearchRequest.searchTerms())
                .addHeader("X-Api-Key", apiKey)
                // Serply is served through Cloudflare, which returns 403 for requests
                // without a browser-like User-Agent header.
                .addHeader("User-Agent", "Mozilla/5.0 (compatible; langchain4j-serply/1.0)")
                .addHeader("Accept", "application/json");

        int pageSize = DEFAULT_PAGE_SIZE;
        if (webSearchRequest.maxResults() != null) {
            pageSize = webSearchRequest.maxResults();
            requestBuilder.addQueryParam("num", String.valueOf(pageSize));
        }

        int start = startOffset(webSearchRequest.startPage(), pageSize);
        if (start > 0) {
            requestBuilder.addQueryParam("start", String.valueOf(start));
        }

        return requestBuilder.build();
    }

    /**
     * Serply paginates with a zero-based {@code start} offset (like Google), while
     * {@link WebSearchRequest#startPage()} is a one-based page number.
     */
    static int startOffset(Integer startPage, int pageSize) {
        if (startPage == null || startPage <= 1) {
            return 0;
        }
        return (startPage - 1) * pageSize;
    }

    static RuntimeException toException(HttpException e) {
        int status = e.statusCode();
        String message = "Serply request failed with HTTP " + status;
        if (!isNullOrBlank(e.getMessage())) {
            String body = e.getMessage().strip();
            if (body.length() > MAX_ERROR_BODY_LENGTH) {
                body = body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
            }
            message += ": " + body;
        }
        if (status == 401 || status == 403) {
            return new AuthenticationException(message, e);
        }
        if (status == 408) {
            return new TimeoutException(message, e);
        }
        if (status == 429) {
            return new RateLimitException(message, e);
        }
        if (status >= 500) {
            return new InternalServerException(message, e);
        }
        return new InvalidRequestException(message, e);
    }

    private static SerplyWebSearchResponse parse(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, SerplyWebSearchResponse.class);
        } catch (Exception e) {
            // A malformed body is not going to fix itself on retry.
            throw new NonRetriableException("Failed to parse Serply response", e);
        }
    }

    static String defaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }
}
