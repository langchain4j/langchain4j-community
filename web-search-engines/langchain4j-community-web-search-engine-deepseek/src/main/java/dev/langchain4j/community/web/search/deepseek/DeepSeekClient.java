package dev.langchain4j.community.web.search.deepseek;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

class DeepSeekClient {
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    DeepSeekClient(
            HttpClientBuilder builder,
            String baseUrl,
            String apiKey,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses) {
        HttpClientBuilder resolved = getOrDefault(builder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient client =
                resolved.connectTimeout(timeout).readTimeout(timeout).build();
        boolean logReq = Boolean.TRUE.equals(logRequests);
        boolean logRes = Boolean.TRUE.equals(logResponses);
        this.httpClient = logReq || logRes ? new LoggingHttpClient(client, logReq, logRes) : client;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
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
                .url(baseUrl, endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .body(body)
                .build();
    }

    private record DeepSeekFetchRequest(String url) {
    }
}
