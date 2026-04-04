package dev.langchain4j.community.model.client;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.streaming.CohereServerSentEventListener;
import dev.langchain4j.community.model.client.chat.streaming.CohereStreamingContent;
import dev.langchain4j.community.model.client.chat.streaming.CohereStreamingData;
import dev.langchain4j.community.model.client.chat.streaming.CohereStreamingStartData;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.community.model.client.chat.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.THINKING;
import static dev.langchain4j.community.model.util.CohereMapper.fromFinishReason;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

@Internal
public class CohereClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public CohereClient(Builder builder) {
        HttpClientBuilder httpClientBuilder = getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(builder.timeout, Duration.ofSeconds(30)))
                .readTimeout(getOrDefault(builder.timeout, Duration.ofSeconds(30)))
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses)  {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.authToken;
    }

    public CohereChatResponse createMessage(CohereChatRequest cohereChatRequest) {
        SuccessfulHttpResponse rawResponse = this.httpClient.execute(toHttpRequest(cohereChatRequest));
        return fromJson(rawResponse.body(), CohereChatResponse.class);
    }

    public void createStreamingMessage(CohereChatRequest cohereChatRequest, StreamingChatResponseHandler handler) {
        this.httpClient.execute(
                toHttpRequest(cohereChatRequest),
                new CohereServerSentEventListener(cohereChatRequest.getModel(), handler)
        );
    }

    private HttpRequest toHttpRequest(CohereChatRequest cohereChatRequest)  {
        return HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "/chat")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "bearer " + apiKey)
                .body(toJson(cohereChatRequest))
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String authToken;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

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

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses =  logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public CohereClient build() { return new CohereClient(this); }
    }
}
