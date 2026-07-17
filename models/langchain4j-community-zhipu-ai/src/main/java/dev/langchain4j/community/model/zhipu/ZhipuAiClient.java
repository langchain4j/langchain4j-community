package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.AuthorizationUtils.getToken;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.finishReasonFrom;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toZhipuAiException;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.tokenUsageFrom;
import static dev.langchain4j.community.model.zhipu.Json.fromJson;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
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
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ZhipuAiClient {

    private static final Logger log = LoggerFactory.getLogger(ZhipuAiClient.class);
    private static final StreamingHandle NO_OP_STREAMING_HANDLE = new StreamingHandle() {
        @Override
        public void cancel() {
            // Zhipu AI does not support streaming cancellation
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

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
                .addHeader("Authorization", getToken(apiKey))
                .body(Json.toJson(request))
                .build();

        try {
            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
            return Json.fromJson(successfulHttpResponse.body(), ChatCompletionResponse.class);
        } catch (HttpException e) {
            throw toZhipuAiException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    EmbeddingResponse embedAll(EmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/embeddings")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", getToken(apiKey))
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

    void streamingChatCompletion(ChatCompletionRequest request, StreamingChatResponseHandler handler) {

        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/chat/completions")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", getToken(apiKey))
                .body(Json.toJson(request))
                .build();
        ServerSentEventListener eventListener = new ServerSentEventListener() {
            final StringBuffer contentBuilder = new StringBuffer();
            final StringBuffer reasoningContentBuilder = new StringBuffer();
            final ToolCallBuilder toolCallBuilder = new ToolCallBuilder();
            TokenUsage tokenUsage;
            FinishReason finishReason;
            ChatCompletionResponse chatCompletionResponse;
            String modelName = null;
            String id = null;

            @Override
            public void onEvent(ServerSentEvent event) {
                try {
                    String data = event.data();
                    if ("[DONE]".equals(data)) {
                        CompleteToolCall completeToolCall = buildCompleteToolCallIfAny();
                        if (completeToolCall != null) {
                            handler.onCompleteToolCall(completeToolCall);
                        }

                        String text = !contentBuilder.isEmpty() ? contentBuilder.toString() : null;
                        String thinking =
                                !reasoningContentBuilder.isEmpty() ? reasoningContentBuilder.toString() : null;

                        List<ToolExecutionRequest> allRequests = toolCallBuilder.allRequests();

                        AiMessage.Builder aiMessageBuilder =
                                AiMessage.builder().text(text).thinking(thinking);

                        if (!isNullOrEmpty(allRequests)) {
                            aiMessageBuilder.toolExecutionRequests(allRequests);
                        }

                        AiMessage aiMessage = aiMessageBuilder.build();
                        ChatResponse response = ChatResponse.builder()
                                .aiMessage(aiMessage)
                                .tokenUsage(tokenUsage)
                                .finishReason(finishReason)
                                .id(id)
                                .modelName(modelName)
                                .build();

                        handler.onCompleteResponse(response);
                    } else {
                        chatCompletionResponse = fromJson(data, ChatCompletionResponse.class);
                        ChatCompletionChoice zhipuChatCompletionChoice =
                                chatCompletionResponse.getChoices().get(0);
                        var delta = zhipuChatCompletionChoice.getDelta();
                        String chunk = delta.getContent();
                        if (isNotNullOrBlank(chunk)) {
                            contentBuilder.append(chunk);
                            handler.onPartialResponse(chunk);
                        }

                        // reasoning content
                        String reasoningChunk = delta.getReasoningContent();
                        if (isNotNullOrBlank(reasoningChunk)) {
                            reasoningContentBuilder.append(reasoningChunk);
                            handler.onPartialThinking(new PartialThinking(reasoningChunk));
                        }

                        if (isNotNullOrBlank(chatCompletionResponse.getId())) {
                            id = chatCompletionResponse.getId();
                        }
                        if (isNotNullOrBlank(chatCompletionResponse.getModel())) {
                            modelName = chatCompletionResponse.getModel();
                        }
                        Usage zhipuUsageInfo = chatCompletionResponse.getUsage();
                        if (zhipuUsageInfo != null) {
                            this.tokenUsage = tokenUsageFrom(zhipuUsageInfo);
                        }

                        String finishReasonString = zhipuChatCompletionChoice.getFinishReason();
                        if (finishReasonString != null) {
                            this.finishReason = finishReasonFrom(finishReasonString);
                        }

                        // tool calls
                        List<ToolCall> toolCalls = delta.getToolCalls();
                        if (!isNullOrEmpty(toolCalls)) {
                            for (ToolCall toolCall : toolCalls) {
                                int index = getOrDefault(toolCall.getIndex(), 0);
                                if (toolCallBuilder.index() != index) {
                                    CompleteToolCall complete = buildCompleteToolCallIfAny();
                                    if (complete != null) {
                                        handler.onCompleteToolCall(complete);
                                    }
                                    toolCallBuilder.updateIndex(index);
                                }

                                String toolCallId = toolCallBuilder.updateId(toolCall.getId());
                                String toolCallName = toolCallBuilder.updateName(
                                        toolCall.getFunction().getName());
                                String partialArguments = toolCall.getFunction().getArguments();

                                if (isNotNullOrBlank(partialArguments)) {
                                    toolCallBuilder.appendArguments(partialArguments);

                                    PartialToolCall partialToolCall = PartialToolCall.builder()
                                            .index(index)
                                            .id(toolCallId)
                                            .name(toolCallName)
                                            .partialArguments(partialArguments)
                                            .build();
                                    handler.onPartialToolCall(
                                            partialToolCall, new PartialToolCallContext(NO_OP_STREAMING_HANDLE));
                                }
                            }
                        }
                    }
                } catch (Exception exception) {
                    RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(exception);
                    withLoggingExceptions(() -> handler.onError(mappedException));
                }
            }

            private CompleteToolCall buildCompleteToolCallIfAny() {
                if (toolCallBuilder.hasRequests()) {
                    return toolCallBuilder.buildAndReset();
                }
                return null;
            }

            @Override
            public void onError(final Throwable t) {
                if (logResponses) {
                    log.debug("onError()", t);
                }
                RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(t);
                withLoggingExceptions(() -> handler.onError(mappedException));
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

    private void handleResponseException(Throwable t, StreamingChatResponseHandler handler) {
        Throwable throwable;
        if (t instanceof HttpException httpException) {
            if (Utils.isNullOrBlank(httpException.getMessage())) {
                throwable = new ZhipuAiException(String.valueOf(httpException.statusCode()), null);
            } else {
                try {
                    ErrorResponse errorResponse = Json.fromJson(httpException.getMessage(), ErrorResponse.class);
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
        handler.onError(throwable);
    }

    ImageResponse imagesGeneration(ImageRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .url(baseUrl, "api/paas/v4/images/generations")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", getToken(apiKey))
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
