package dev.langchain4j.community.web.search.serpapi;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import com.fasterxml.jackson.databind.JsonNode;
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
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.CompletableFutureUtils;
import dev.langchain4j.internal.RetryUtils.RetryPolicy;
import dev.langchain4j.web.search.WebSearchRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SerpApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://serpapi.com";
    private static final int PAGE_SIZE = 10;
    private static final int MAX_ERROR_BODY_LENGTH = 300;

    /**
     * Engines that understand Google's {@code hl}/{@code gl}/{@code start}/{@code safe} parameters.
     */
    private static final Set<String> GOOGLE_FAMILY = Set.of("google", "google_news", "google_scholar", "youtube");

    private final String apiKey;
    private final String baseUrl;
    private final String engine;
    private final String location;
    private final boolean noCache;
    private final Map<String, Object> defaultParameters;
    private final HttpClient httpClient;
    private final RetryPolicy retryPolicy;

    SerpApiClient(
            String apiKey,
            String baseUrl,
            String engine,
            String location,
            boolean noCache,
            Map<String, Object> defaultParameters,
            HttpClient httpClient,
            RetryPolicy retryPolicy) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.engine = engine;
        this.location = location;
        this.noCache = noCache;
        this.defaultParameters = defaultParameters == null ? Map.of() : Map.copyOf(defaultParameters);
        this.httpClient = httpClient;
        this.retryPolicy = retryPolicy;
    }

    static HttpClient buildHttpClient(
            HttpClientBuilder httpClientBuilder, Duration timeout, boolean logRequests, boolean logResponses) {
        HttpClientBuilder builder =
                httpClientBuilder != null ? httpClientBuilder : HttpClientBuilderLoader.loadHttpClientBuilder();
        HttpClient client = builder.connectTimeout(timeout).readTimeout(timeout).build();
        return (logRequests || logResponses) ? new MaskingLoggingHttpClient(client, logRequests, logResponses) : client;
    }

    /**
     * Executes the search. Only transient failures (HTTP 429 throughput limits, 5xx, transport errors)
     * are retried; authentication errors, bad requests and an exhausted search quota fail fast.
     */
    JsonNode search(WebSearchRequest webSearchRequest) {
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

    /**
     * Executes the search asynchronously, without retries. Cancelling the returned future cancels
     * the underlying HTTP request.
     */
    CompletableFuture<JsonNode> searchAsync(WebSearchRequest webSearchRequest) {
        CompletableFuture<SuccessfulHttpResponse> httpFuture = httpClient.executeAsync(buildRequest(webSearchRequest));
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        httpFuture.whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause =
                        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                result.completeExceptionally(cause instanceof HttpException e ? toException(e) : cause);
                return;
            }
            try {
                result.complete(parse(response.body()));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            }
        });
        CompletableFutureUtils.propagateCancellation(result, httpFuture);
        return result;
    }

    /**
     * Builds the query parameters. Precedence, from lowest to highest: builder default parameters,
     * fields mapped from the {@link WebSearchRequest}, then {@link WebSearchRequest#additionalParams()}.
     * {@code engine}, the query and {@code api_key} are always set last.
     */
    HttpRequest buildRequest(WebSearchRequest webSearchRequest) {
        Map<String, String> params = new LinkedHashMap<>();
        defaultParameters.forEach((key, value) -> putIfPresent(params, key, value));

        if (GOOGLE_FAMILY.contains(engine)) {
            putIfPresent(params, "hl", webSearchRequest.language());
            if (!engine.equals("google_scholar")) {
                putIfPresent(params, "gl", webSearchRequest.geoLocation());
            }
        }
        if (engine.equals("google")) {
            putIfPresent(params, "location", location);
            if (webSearchRequest.safeSearch() != null) {
                params.put("safe", webSearchRequest.safeSearch() ? "active" : "off");
            }
        }
        if (engine.equals("google") || engine.equals("google_scholar")) {
            int start = startOffset(webSearchRequest.startPage(), webSearchRequest.startIndex());
            if (start > 0) {
                params.put("start", String.valueOf(start));
            }
        }
        if (noCache) {
            params.put("no_cache", "true");
        }
        if (webSearchRequest.additionalParams() != null) {
            webSearchRequest.additionalParams().forEach((key, value) -> putIfPresent(params, key, value));
        }

        params.put("engine", engine);
        params.put(queryParameterName(engine), webSearchRequest.searchTerms());
        params.put("api_key", apiKey);

        return HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "search")
                .addQueryParams(params)
                .addHeader("Accept", "application/json")
                .build();
    }

    static String queryParameterName(String engine) {
        return engine.equals("youtube") ? "search_query" : "q";
    }

    /**
     * SerpApi paginates Google-family engines with a zero-based {@code start} offset.
     * {@link WebSearchRequest#startIndex()} is treated as that offset directly and wins over
     * {@link WebSearchRequest#startPage()}, which is one-based and assumes pages of 10 results.
     */
    static int startOffset(Integer startPage, Integer startIndex) {
        if (startIndex != null) {
            return Math.max(startIndex, 0);
        }
        if (startPage == null || startPage <= 1) {
            return 0;
        }
        return (startPage - 1) * PAGE_SIZE;
    }

    private static void putIfPresent(Map<String, String> params, String key, Object value) {
        if (value != null && !isNullOrBlank(value.toString())) {
            params.put(key, value.toString());
        }
    }

    static RuntimeException toException(HttpException e) {
        int status = e.statusCode();
        String body = errorText(e.getMessage());
        String message = "SerpApi request failed with HTTP " + status + (body.isEmpty() ? "" : ": " + body);
        if (status == 401 || status == 403) {
            return new AuthenticationException(message, e);
        }
        if (status == 408) {
            return new TimeoutException(message, e);
        }
        if (status == 429) {
            if (body.toLowerCase().contains("run out of searches")) {
                // Retrying cannot help when the monthly quota is exhausted.
                return new NonRetriableException(message, new RateLimitException(message, e));
            }
            return new RateLimitException(message, e);
        }
        if (status >= 500) {
            return new InternalServerException(message, e);
        }
        return new InvalidRequestException(message, e);
    }

    /**
     * Extracts the {@code error} field of a JSON error body, falling back to the raw body.
     */
    private static String errorText(String body) {
        if (isNullOrBlank(body)) {
            return "";
        }
        String text = body.strip();
        try {
            JsonNode error = OBJECT_MAPPER.readTree(text).path("error");
            if (error.isTextual()) {
                text = error.asText();
            }
        } catch (Exception ignored) {
            // not JSON, use the raw body
        }
        return text.length() > MAX_ERROR_BODY_LENGTH ? text.substring(0, MAX_ERROR_BODY_LENGTH) + "..." : text;
    }

    /**
     * Parses a successful response. SerpApi reports "no results" as HTTP 200 with an {@code error}
     * field; that case is returned as-is (the engine maps it to an empty result list), while any
     * other {@code error} or an {@code Error} search status is turned into an exception.
     */
    static JsonNode parse(String body) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(body);
        } catch (Exception e) {
            // A malformed body is not going to fix itself on retry.
            throw new NonRetriableException("Failed to parse SerpApi response", e);
        }
        String error = root.path("error").asText("");
        if (isNoResults(root, error)) {
            return root;
        }
        if (!error.isEmpty()
                || "Error".equals(root.path("search_metadata").path("status").asText())) {
            throw new InternalServerException("SerpApi search failed: " + (error.isEmpty() ? "status Error" : error));
        }
        return root;
    }

    private static boolean isNoResults(JsonNode root, String error) {
        return error.contains("hasn't returned any results")
                || "Fully empty"
                        .equals(root.path("search_information")
                                .path("organic_results_state")
                                .asText());
    }

    static String defaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    /**
     * LangChain4j's {@code LoggingHttpClient} masks secret headers but logs URLs verbatim, which
     * would leak the {@code api_key} query parameter. This client logs with the key masked.
     */
    static class MaskingLoggingHttpClient implements HttpClient {

        private static final Logger log = LoggerFactory.getLogger(SerpApiClient.class);
        private static final Pattern API_KEY = Pattern.compile("(api_key=)[^&]*");

        private final HttpClient delegate;
        private final boolean logRequests;
        private final boolean logResponses;

        MaskingLoggingHttpClient(HttpClient delegate, boolean logRequests, boolean logResponses) {
            this.delegate = delegate;
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        static String mask(String url) {
            return API_KEY.matcher(url).replaceAll("$1****");
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            logRequest(request);
            SuccessfulHttpResponse response = delegate.execute(request);
            logResponse(response);
            return response;
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            logRequest(request);
            CompletableFuture<SuccessfulHttpResponse> future = delegate.executeAsync(request);
            future.thenAccept(this::logResponse);
            return future;
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            delegate.execute(request, parser, listener);
        }

        private void logRequest(HttpRequest request) {
            if (logRequests) {
                log.info("HTTP request: {} {}", request.method(), mask(request.url()));
            }
        }

        private void logResponse(SuccessfulHttpResponse response) {
            if (logResponses) {
                log.info("HTTP response: status {}, body: {}", response.statusCode(), response.body());
            }
        }
    }
}
