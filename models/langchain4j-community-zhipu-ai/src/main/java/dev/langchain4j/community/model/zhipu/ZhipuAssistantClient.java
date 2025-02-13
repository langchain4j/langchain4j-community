package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.aiMessageFrom;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.finishReasonFrom;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.getFinishReason;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.toChatErrorResponse;
import static dev.langchain4j.community.model.zhipu.DefaultZhipuAiHelper.tokenUsageFrom;
import static dev.langchain4j.community.model.zhipu.Json.OBJECT_MAPPER;
import static retrofit2.converter.jackson.JacksonConverterFactory.create;

import dev.langchain4j.community.model.zhipu.assistant.AssistantCompletion;
import dev.langchain4j.community.model.zhipu.assistant.AssistantExtraInput;
import dev.langchain4j.community.model.zhipu.assistant.AssistantKeyValuePair;
import dev.langchain4j.community.model.zhipu.assistant.AssistantNodeData;
import dev.langchain4j.community.model.zhipu.assistant.AssistantSupportResponse;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationId;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationRequest;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationResponse;
import dev.langchain4j.community.model.zhipu.assistant.problem.Problems;
import dev.langchain4j.community.model.zhipu.assistant.problem.ProblemsResponse;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.zhipu.shared.Usage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;

public class ZhipuAssistantClient {
    private static final Logger log = LoggerFactory.getLogger(ZhipuAssistantClient.class);

    private final ZhipuAiApi zhipuAiApi;
    private final OkHttpClient okHttpClient;
    private final Boolean logResponses;

    public ZhipuAssistantClient(Builder builder) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.callTimeout)
                .connectTimeout(builder.connectTimeout)
                .readTimeout(builder.readTimeout)
                .writeTimeout(builder.writeTimeout)
                .addInterceptor(new AuthorizationInterceptor(builder.apiKey));

        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
        }

        this.logResponses = builder.logResponses;
        if (builder.logResponses) {
            okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
        }

        this.okHttpClient = okHttpClientBuilder.build();
        Retrofit retrofit = (new Retrofit.Builder())
                .baseUrl(Utils.ensureTrailingForwardSlash(builder.baseUrl))
                .client(this.okHttpClient)
                .addConverterFactory(create(OBJECT_MAPPER))
                .build();
        this.zhipuAiApi = retrofit.create(ZhipuAiApi.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<AssistantKeyValuePair> variables(String appId) {
        retrofit2.Response<AssistantSupportResponse> retrofitResponse;
        try {
            retrofitResponse = zhipuAiApi.variables(appId).execute();
            if (retrofitResponse.isSuccessful()) {
                final AssistantSupportResponse body = retrofitResponse.body();
                if (Objects.nonNull(body)) {
                    if (!body.isSuccess()) {
                        log.error("获取智能体输入参数失败，原因为：【{}】", body.getMessage());
                        throw new ZhipuAiException(body.getCode() + "", body.getMessage());
                    }
                    return body.getData();
                }
                throw toException(retrofitResponse);
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ConversationId conversation(String appId) {
        retrofit2.Response<ConversationResponse> retrofitResponse;
        try {
            retrofitResponse = zhipuAiApi.conversation(appId).execute();
            if (retrofitResponse.isSuccessful()) {
                final ConversationResponse body = retrofitResponse.body();
                if (Objects.nonNull(body)) {
                    if (!body.isSuccess()) {
                        log.error("创建新会话失败，原因为：【{}】", body.getMessage());
                        throw new ZhipuAiException(body.getCode() + "", body.getMessage());
                    }
                    return body.getData();
                }
                throw toException(retrofitResponse);
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ConversationId generate(ConversationRequest request) {
        retrofit2.Response<ConversationResponse> retrofitResponse;
        try {
            retrofitResponse = zhipuAiApi.generateRequestId(request).execute();
            if (retrofitResponse.isSuccessful()) {
                final ConversationResponse body = retrofitResponse.body();
                if (Objects.nonNull(body)) {
                    if (!body.isSuccess()) {
                        log.error("创建对话或创作请求失败，原因为：【{}】", body.getMessage());
                        throw new ZhipuAiException(body.getCode() + "", body.getMessage());
                    }
                    return body.getData();
                }
                throw toException(retrofitResponse);
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void sseInvoke(String requestId, StreamingResponseHandler<AiMessage> handler) {
        EventSourceListener eventSourceListener = new EventSourceListener() {
            final StringBuffer contentBuilder = new StringBuffer();
            TokenUsage tokenUsage;
            FinishReason finishReason;
            AssistantCompletion completion;

            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull okhttp3.Response response) {
                if (logResponses) {
                    log.debug("onOpen()");
                }
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
                if (logResponses) {
                    log.debug("onEvent(): type:{} | data: {}", type, data);
                }
                if ("[DONE]".equals(data) || "finish".equals(type)) {
                    AiMessage aiMessage = AiMessage.from(contentBuilder.toString());
                    Response<AiMessage> response = Response.from(aiMessage, tokenUsage, finishReason);
                    handler.onComplete(response);
                } else if ("errorhandle".equalsIgnoreCase(type) || "errorhandler".equalsIgnoreCase(type)) {
                    try {
                        completion = OBJECT_MAPPER.readValue(data, AssistantCompletion.class);
                        Usage usageInfo = completion.getUsage();
                        if (usageInfo != null) {
                            this.tokenUsage = tokenUsageFrom(usageInfo);
                        }
                        AiMessage aiMessage = AiMessage.from(completion.getMsg());
                        Response<AiMessage> response = Response.from(aiMessage, this.tokenUsage, FinishReason.OTHER);
                        handler.onComplete(response);
                    } catch (Exception exception) {
                        handleResponseException(exception, handler);
                    }
                } else {
                    try {
                        completion = OBJECT_MAPPER.readValue(data, AssistantCompletion.class);
                        String chunk = completion.getMsg();
                        if (chunk != null) {
                            contentBuilder.append(chunk);
                            handler.onNext(chunk);
                        }
                        AssistantExtraInput extraInput = completion.getExtraInput();
                        if (extraInput != null) {
                            final AssistantNodeData nodeData = extraInput.getNodeData();
                            if (nodeData != null) {
                                if ("finished".equals(nodeData.getNodeStatus())) {
                                    this.finishReason = FinishReason.STOP;
                                } else if ("sensitive".equals(nodeData.getNodeStatus())) {
                                    this.finishReason = FinishReason.CONTENT_FILTER;
                                }
                            }
                        }
                        Usage usageInfo = completion.getUsage();
                        if (usageInfo != null) {
                            this.tokenUsage = tokenUsageFrom(usageInfo);
                        }
                    } catch (Exception exception) {
                        handleResponseException(exception, handler);
                    }
                }
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (logResponses) {
                    log.debug("onFailure()", t);
                }
                Throwable throwable = Utils.getOrDefault(t, new ZhipuAiException(response));
                handleResponseException(throwable, handler);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                if (logResponses) {
                    log.debug("onClosed()");
                }
            }
        };
        EventSources.createFactory(this.okHttpClient)
                .newEventSource(zhipuAiApi.sseInvoke(requestId).request(), eventSourceListener);
    }

    public Problems sessionRecord(String appId, String conversationId) {
        retrofit2.Response<ProblemsResponse> retrofitResponse;
        try {
            retrofitResponse = zhipuAiApi.sessionRecord(appId, conversationId).execute();
            if (retrofitResponse.isSuccessful()) {
                final ProblemsResponse body = retrofitResponse.body();
                if (Objects.nonNull(body)) {
                    if (!body.isSuccess()) {
                        log.error("获取推荐问题失败，原因为：【{}】", body.getMessage());
                        throw new ZhipuAiException("1200", body.getMessage());
                    }
                    return body.getData();
                }
                throw toException(retrofitResponse);
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleResponseException(Throwable throwable, StreamingResponseHandler<AiMessage> handler) {

        if (throwable instanceof ZhipuAiException) {
            ChatCompletionResponse errorResponse = toChatErrorResponse(throwable);
            Response<AiMessage> messageResponse = Response.from(
                    aiMessageFrom(errorResponse),
                    tokenUsageFrom(errorResponse.getUsage()),
                    finishReasonFrom(getFinishReason(throwable)));
            handler.onComplete(messageResponse);
        } else {
            handler.onError(throwable);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> retrofitResponse) throws IOException {
        int code = retrofitResponse.code();
        if (code >= 400) {
            try (ResponseBody errorBody = retrofitResponse.errorBody()) {
                if (errorBody != null) {
                    String errorBodyString = errorBody.string();
                    String errorMessage = String.format("status code: %s; body: %s", code, errorBodyString);
                    log.error("Error response: {}", errorMessage);
                    return new RuntimeException(errorMessage);
                }
            }
        }
        return new RuntimeException(retrofitResponse.message());
    }

    public static class Builder {
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

        public Builder baseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                return this;
            } else {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                this.apiKey = apiKey;
                return this;
            } else {
                throw new IllegalArgumentException("apiKey cannot be null or empty. ");
            }
        }

        public Builder callTimeout(Duration callTimeout) {
            if (callTimeout == null) {
                throw new IllegalArgumentException("callTimeout cannot be null");
            } else {
                this.callTimeout = callTimeout;
                return this;
            }
        }

        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null) {
                throw new IllegalArgumentException("connectTimeout cannot be null");
            } else {
                this.connectTimeout = connectTimeout;
                return this;
            }
        }

        public Builder readTimeout(Duration readTimeout) {
            if (readTimeout == null) {
                throw new IllegalArgumentException("readTimeout cannot be null");
            } else {
                this.readTimeout = readTimeout;
                return this;
            }
        }

        public Builder writeTimeout(Duration writeTimeout) {
            if (writeTimeout == null) {
                throw new IllegalArgumentException("writeTimeout cannot be null");
            } else {
                this.writeTimeout = writeTimeout;
                return this;
            }
        }

        public Builder logRequests() {
            return this.logRequests(true);
        }

        public Builder logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }

            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses() {
            return this.logResponses(true);
        }

        public Builder logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }

            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAssistantClient build() {
            return new ZhipuAssistantClient(this);
        }
    }
}
