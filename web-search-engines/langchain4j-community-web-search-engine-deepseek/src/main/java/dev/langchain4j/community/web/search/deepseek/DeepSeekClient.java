package dev.langchain4j.community.web.search.deepseek;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

class DeepSeekClient {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String TOOLS_PATH = "v1/tools/";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    static Builder builder() {
        return new Builder();
    }

    private DeepSeekClient(Builder builder) {
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.baseUrl = ensureNotBlank(normalizeBaseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL)), "baseUrl");

        HttpClientBuilder resolved =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        Duration timeout = getOrDefault(builder.timeout, DEFAULT_TIMEOUT);
        HttpClient client =
                resolved.connectTimeout(timeout).readTimeout(timeout).build();

        boolean logReq = Boolean.TRUE.equals(builder.logRequests);
        boolean logRes = Boolean.TRUE.equals(builder.logResponses);
        this.httpClient = logReq || logRes ? new LoggingHttpClient(client, logReq, logRes) : client;
    }

    DeepSeekSearchResponse search(DeepSeekSearchRequest request) {
        return DeepSeekJsonUtils.fromJson(
                httpClient
                        .execute(request("search", DeepSeekJsonUtils.toJson(request)))
                        .body(),
                DeepSeekSearchResponse.class);
    }

    CompletableFuture<DeepSeekSearchResponse> searchAsync(DeepSeekSearchRequest request) {
        CompletableFuture<SuccessfulHttpResponse> response =
                httpClient.executeAsync(request("search", DeepSeekJsonUtils.toJson(request)));
        CompletableFuture<DeepSeekSearchResponse> result =
                response.thenApply(value -> DeepSeekJsonUtils.fromJson(value.body(), DeepSeekSearchResponse.class));
        propagateCancellation(result, response);
        return result;
    }

    DeepSeekFetchResponse fetch(String url) {
        return DeepSeekJsonUtils.fromJson(
                httpClient
                        .execute(request("fetch", DeepSeekJsonUtils.toJson(new DeepSeekFetchRequest(url))))
                        .body(),
                DeepSeekFetchResponse.class);
    }

    private HttpRequest request(String endpoint, String body) {
        return HttpRequest.builder()
                .method(POST)
                .url(baseUrl, TOOLS_PATH + endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .body(body)
                .build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        DeepSeekClient build() {
            return new DeepSeekClient(this);
        }
    }

    private record DeepSeekFetchRequest(String url) {}
}
