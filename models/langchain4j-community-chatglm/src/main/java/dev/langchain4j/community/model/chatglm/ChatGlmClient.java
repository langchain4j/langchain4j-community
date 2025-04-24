package dev.langchain4j.community.model.chatglm;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.internal.Json;
import java.time.Duration;

class ChatGlmClient {

    private static final int statusCode = 200;
    private final HttpClient httpClient;
    private final String baseUrl;

    public ChatGlmClient(Builder builder) {
        this.baseUrl = ensureNotNull(builder.baseUrl, "baseUrl");
        builder.timeout = getOrDefault(builder.timeout, ofSeconds(60));
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader.loadHttpClientBuilder());
        HttpClient httpClient = httpClientBuilder
                .connectTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests || builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl)
                    .body(Json.toJson(request))
                    .addHeader("Content-Type", "application/json")
                    .build();
            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
            ChatCompletionResponse response =
                    Json.fromJson(successfulHttpResponse.body(), ChatCompletionResponse.class);
            if (response == null || response.getStatus() != statusCode) {
                throw toException(successfulHttpResponse);
            }
            return response;
        } catch (HttpException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(SuccessfulHttpResponse response) {
        String body = response.body();
        int code = response.statusCode();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
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

        Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        ChatGlmClient build() {
            return new ChatGlmClient(this);
        }
    }
}
