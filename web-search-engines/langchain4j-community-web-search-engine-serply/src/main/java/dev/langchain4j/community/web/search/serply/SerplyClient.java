package dev.langchain4j.community.web.search.serply;

import static dev.langchain4j.http.client.HttpMethod.GET;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.web.search.WebSearchRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

class SerplyClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.serply.io";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    SerplyClient(String apiKey, String baseUrl, Duration timeout, boolean logRequests, boolean logResponses) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;

        HttpClientBuilder builder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);

        HttpClient client = builder.build();
        this.httpClient =
                (logRequests || logResponses) ? new LoggingHttpClient(client, logRequests, logResponses) : client;
    }

    SerplyWebSearchResponse search(WebSearchRequest webSearchRequest) {
        return RetryUtils.retryPolicyBuilder()
                .maxRetries(2)
                .delayMillis(1000)
                .build()
                .withRetry(() -> {
                    HttpRequest.Builder requestBuilder = HttpRequest.builder()
                            .method(GET)
                            .url(baseUrl, "v1/search")
                            .addQueryParam("q", webSearchRequest.searchTerms())
                            .addHeader("X-Api-Key", apiKey)
                            // Serply is served through Cloudflare, which returns 403 for requests
                            // without a browser-like User-Agent header.
                            .addHeader("User-Agent", "Mozilla/5.0 (compatible; langchain4j-serply/1.0)")
                            .addHeader("Accept", "application/json");

                    if (webSearchRequest.maxResults() != null) {
                        requestBuilder.addQueryParam("num", String.valueOf(webSearchRequest.maxResults()));
                    }

                    SuccessfulHttpResponse response = httpClient.execute(requestBuilder.build());
                    return parse(response.body());
                });
    }

    private SerplyWebSearchResponse parse(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, SerplyWebSearchResponse.class);
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Failed to parse Serply response", e));
        }
    }

    static String defaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }
}
