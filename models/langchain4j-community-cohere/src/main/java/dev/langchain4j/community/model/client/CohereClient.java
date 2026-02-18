package dev.langchain4j.community.model.client;

import dev.langchain4j.Internal;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.CohereChatResponse;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;

import java.time.Duration;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.Utils.getOrDefault;

@Internal
public class CohereClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authToken;

    public CohereClient(Builder builder) {
        HttpClientBuilder httpClientBuilder = getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        this.httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(builder.timeout, Duration.ofSeconds(30)))
                .readTimeout(getOrDefault(builder.timeout, Duration.ofSeconds(30)))
                .build();

        this.baseUrl = builder.baseUrl;
        this.authToken = builder.authToken;
    }

    public CohereChatResponse createMessage(CohereChatRequest cohereChatRequest) {
        SuccessfulHttpResponse rawResponse = this.httpClient.execute(toHttpRequest(cohereChatRequest));
        return fromJson(rawResponse.body(), CohereChatResponse.class);
    }

    private HttpRequest toHttpRequest(CohereChatRequest cohereChatRequest)  {
        return HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "/chat")
                .addHeader("Authorization", "bearer " + authToken)
                .body(toJson(cohereChatRequest))
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String authToken;
        private Duration timeout;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CohereClient build() { return new CohereClient(this); }
    }
}
