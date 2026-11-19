package dev.langchain4j.community.web.search.sofya;

import static dev.langchain4j.community.web.search.sofya.SofyaJsonUtils.fromJson;
import static dev.langchain4j.community.web.search.sofya.SofyaJsonUtils.toJson;
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

class SofyaClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    SofyaClient(
            HttpClientBuilder httpClientBuilder,
            String baseUrl,
            String apiKey,
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
    }

    SofyaSearchResponse search(SofyaSearchRequest searchRequest) {
        SuccessfulHttpResponse response = httpClient.execute(toHttpRequest(searchRequest));
        return fromJson(response.body(), SofyaSearchResponse.class);
    }

    CompletableFuture<SofyaSearchResponse> searchAsync(SofyaSearchRequest searchRequest) {
        CompletableFuture<SuccessfulHttpResponse> httpFuture = httpClient.executeAsync(toHttpRequest(searchRequest));
        CompletableFuture<SofyaSearchResponse> result =
                httpFuture.thenApply(response -> fromJson(response.body(), SofyaSearchResponse.class));
        // cancelling a thenApply stage alone does not cancel its upstream, so link it back to the HTTP call
        propagateCancellation(result, httpFuture);
        return result;
    }

    private HttpRequest toHttpRequest(SofyaSearchRequest searchRequest) {
        return HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "v1/search")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .body(toJson(searchRequest))
                .build();
    }
}
