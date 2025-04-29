package dev.langchain4j.community.model.zhipu;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.aiMessageFrom;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.finishReasonFrom;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.getFinishReason;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.specificationsFrom;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.toChatErrorResponse;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.tokenUsageFrom;
import static dev.langchain4j.community.model.zhipu.Json.OBJECT_MAPPER;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.zhipu.chat.ToolCall;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.zhipu.image.ImageRequest;
import dev.langchain4j.community.model.zhipu.image.ImageResponse;
import dev.langchain4j.community.model.zhipu.shared.ErrorResponse;
import dev.langchain4j.community.model.zhipu.shared.Usage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ZhipuAiClient {

    private static final Logger log = LoggerFactory.getLogger(ZhipuAiClient.class);
    private final Boolean logResponses;

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    ZhipuAiClient(Builder builder) {
        this.logResponses = builder.logResponses;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        HttpClientBuilder httpClientBuilder = HttpClientBuilderLoader.loadHttpClientBuilder();

        HttpClient client = httpClientBuilder
                .readTimeout(builder.readTimeout)
                .connectTimeout(builder.connectTimeout)
                .build();
        if (builder.logRequests || builder.logResponses) {
            this.httpClient = new LoggingHttpClient(client, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = client;
        }
    }

    static Builder builder() {
        return new Builder();
    }

    ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/chat/completions")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader(AUTHORIZATION, AuthorizationUtils.getToken(apiKey))
                .body(Json.toJson(request))
                .build();

        try {
            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
            return Json.fromJson(successfulHttpResponse.body(), ChatCompletionResponse.class);
        } catch (RuntimeException e) {
            return toChatErrorResponse(e);
        }
    }

    EmbeddingResponse embedAll(EmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/embeddings")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader(AUTHORIZATION, AuthorizationUtils.getToken(apiKey))
                .body(Json.toJson(request))
                .build();
        try {
            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
            return Json.fromJson(successfulHttpResponse.body(), EmbeddingResponse.class);
        } catch (HttpException e) {
            logHttpException(e);
            throw new RuntimeException(e);
        }
    }

    private void logHttpException(HttpException e) {
        int statusCode = e.statusCode();
        String errorBodyString = e.getMessage();
        String errorMessage = String.format("status code: %s; body: %s", statusCode, errorBodyString);
        log.error("Error response: {}", errorMessage);
    }

    void streamingChatCompletion(
            ChatCompletionRequest request,
            StreamingChatResponseHandler handler,
            List<ChatModelListener> listeners,
            ChatModelRequestContext requestContext,
            ModelProvider provider) {

        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/chat/completions")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader(AUTHORIZATION, AuthorizationUtils.getToken(apiKey))
                .body(Json.toJson(request))
                .build();
        ServerSentEventListener eventListener = new ServerSentEventListener() {
            final StringBuffer contentBuilder = new StringBuffer();
            List<ToolExecutionRequest> specifications;
            TokenUsage tokenUsage;
            FinishReason finishReason;
            ChatCompletionResponse chatCompletionResponse;

            @Override
            public void onEvent(final ServerSentEvent event) {
                String data = event.data();
                if ("[DONE]".equals(data)) {
                    AiMessage aiMessage;
                    if (isNullOrEmpty(specifications)) {
                        aiMessage = AiMessage.from(contentBuilder.toString());
                    } else {
                        aiMessage = AiMessage.from(specifications);
                    }
                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(aiMessage)
                            .tokenUsage(tokenUsage)
                            .finishReason(finishReason)
                            .build();

                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            response, requestContext.chatRequest(), provider, requestContext.attributes());
                    for (ChatModelListener listener : listeners) {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    }

                    handler.onCompleteResponse(response);
                } else {
                    try {
                        chatCompletionResponse = OBJECT_MAPPER.readValue(data, ChatCompletionResponse.class);
                        ChatCompletionChoice zhipuChatCompletionChoice =
                                chatCompletionResponse.getChoices().get(0);
                        String chunk = zhipuChatCompletionChoice.getDelta().getContent();
                        contentBuilder.append(chunk);
                        handler.onPartialResponse(chunk);
                        Usage zhipuUsageInfo = chatCompletionResponse.getUsage();
                        if (zhipuUsageInfo != null) {
                            this.tokenUsage = tokenUsageFrom(zhipuUsageInfo);
                        }

                        String finishReasonString = zhipuChatCompletionChoice.getFinishReason();
                        if (finishReasonString != null) {
                            this.finishReason = finishReasonFrom(finishReasonString);
                        }

                        List<ToolCall> toolCalls =
                                zhipuChatCompletionChoice.getDelta().getToolCalls();
                        if (!isNullOrEmpty(toolCalls)) {
                            this.specifications = specificationsFrom(toolCalls);
                        }
                    } catch (Exception exception) {
                        handleResponseException(exception, handler, requestContext, provider, listeners);
                    }
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (logResponses) {
                    log.debug("onError()", t);
                }
                Throwable throwable;
                if (t instanceof HttpException httpException) {
                    if (Utils.isNullOrBlank(httpException.getMessage())) {
                        throwable = new ZhipuAiException(String.valueOf(httpException.statusCode()), null);
                    } else {
                        try {
                            ErrorResponse errorResponse =
                                    Json.fromJson(httpException.getMessage(), ErrorResponse.class);
                            throwable = Utils.getOrDefault(
                                    new ZhipuAiException(
                                            errorResponse.getError().get("code"),
                                            errorResponse.getError().get("message")),
                                    t);
                        } catch (Exception ignored) {
                            throwable = new ZhipuAiException(String.valueOf(httpException.statusCode()), null);
                        }
                    }
                } else {
                    throwable = t;
                }
                handleResponseException(throwable, handler, requestContext, provider, listeners);
            }

            @Override
            public void onClose() {
                if (logResponses) {
                    log.debug("onClosed()");
                }
            }
        };
        httpClient.execute(httpRequest, eventListener);
    }

    private void handleResponseException(
            Throwable throwable,
            StreamingChatResponseHandler handler,
            ChatModelRequestContext requestContext,
            ModelProvider provider,
            List<ChatModelListener> listeners) {
        ChatModelErrorContext errorContext = new ChatModelErrorContext(
                throwable, requestContext.chatRequest(), provider, requestContext.attributes());

        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e2) {
                log.warn("Exception while calling model listener", e2);
            }
        });

        if (throwable instanceof ZhipuAiException) {
            ChatCompletionResponse errorResponse = toChatErrorResponse(throwable);
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(aiMessageFrom(errorResponse))
                    .tokenUsage(tokenUsageFrom(errorResponse.getUsage()))
                    .finishReason(finishReasonFrom(getFinishReason(throwable)))
                    .build();
            handler.onCompleteResponse(response);
        } else {
            handler.onError(throwable);
        }
    }

    ImageResponse imagesGeneration(ImageRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/images/generations")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader(AUTHORIZATION, AuthorizationUtils.getToken(apiKey))
                .body(Json.toJson(request))
                .build();
        try {
            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
            return Json.fromJson(successfulHttpResponse.body(), ImageResponse.class);
        } catch (HttpException e) {
            logHttpException(e);
            throw new RuntimeException(e);
        }
    }

    static class Builder {

        private String baseUrl;
        private String apiKey;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private boolean logRequests;
        private boolean logResponses;

        private Builder() {
            this.baseUrl = "https://open.bigmodel.cn/";
            this.callTimeout = Duration.ofSeconds(60L);
            this.connectTimeout = Duration.ofSeconds(60L);
            this.readTimeout = Duration.ofSeconds(60L);
            this.writeTimeout = Duration.ofSeconds(60L);
        }

        Builder baseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                return this;
            } else {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
        }

        Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                this.apiKey = apiKey;
                return this;
            } else {
                throw new IllegalArgumentException("apiKey cannot be null or empty. ");
            }
        }

        Builder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        Builder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        Builder logRequests() {
            return this.logRequests(true);
        }

        Builder logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }

            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses() {
            return this.logResponses(true);
        }

        Builder logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }

            this.logResponses = logResponses;
            return this;
        }

        ZhipuAiClient build() {
            return new ZhipuAiClient(this);
        }
    }
}
