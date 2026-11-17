package dev.langchain4j.community.web.search.keenable;

import static dev.langchain4j.community.web.search.keenable.KeenableJsonUtils.fromJson;
import static dev.langchain4j.community.web.search.keenable.KeenableJsonUtils.toJson;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

class KeenableClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String appTitle;

    KeenableClient(
            HttpClientBuilder httpClientBuilder,
            String baseUrl,
            String apiKey,
            String appTitle,
            Duration timeout,
            Boolean logRequests,
            Boolean logResponses) {
        HttpClientBuilder builder = getOrDefault(httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient client = builder.connectTimeout(timeout).readTimeout(timeout).build();

        boolean logReq = logRequests != null && logRequests;
        boolean logRes = logResponses != null && logResponses;
        this.httpClient = logReq || logRes ? new LoggingHttpClient(client, logReq, logRes) : client;

        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.appTitle = appTitle;
    }

    KeenableSearchResponse search(KeenableSearchRequest searchRequest) {
        SuccessfulHttpResponse response = httpClient.execute(toHttpRequest(searchRequest));
        return fromJson(response.body(), KeenableSearchResponse.class);
    }

    CompletableFuture<KeenableSearchResponse> searchAsync(KeenableSearchRequest searchRequest) {
        CompletableFuture<SuccessfulHttpResponse> httpFuture = httpClient.executeAsync(toHttpRequest(searchRequest));
        CompletableFuture<KeenableSearchResponse> result =
                httpFuture.thenApply(response -> fromJson(response.body(), KeenableSearchResponse.class));
        // cancelling a thenApply stage alone does not cancel its upstream, so link it back to the HTTP call
        propagateCancellation(result, httpFuture);
        return result;
    }

    private HttpRequest toHttpRequest(KeenableSearchRequest searchRequest) {
        boolean keyless = isNullOrBlank(apiKey);
        HttpRequest.Builder builder = HttpRequest.builder()
                .method(POST)
                // without a key, the public endpoint is used: it is rate-limited per IP and needs X-Keenable-Title
                .url(baseUrl, keyless ? "search/public" : "search")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Keenable-Title", appTitle);
        if (!keyless) {
            builder.addHeader("X-API-Key", apiKey);
        }
        return builder.body(toJson(searchRequest)).build();
    }
}
