package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.getSystemMessage;
import static dev.langchain4j.community.model.qianfan.QianfanChatModelNameEnum.fromModelName;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.qianfan.client.QianfanClient;
import dev.langchain4j.community.model.qianfan.client.SyncOrAsyncOrStreaming;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.qianfan.spi.QianfanStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.Proxy;
import java.util.List;
import java.util.Objects;

/**
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class QianfanStreamingChatModel implements StreamingChatModel {

    private final QianfanClient client;
    private final List<ChatModelListener> listeners;

    private final ChatRequestParameters defaultRequestParameters;

    /* TODO: we need QianfanChatRequestParameters to customize parameters */

    private final String endpoint;
    private final String userId;

    public QianfanStreamingChatModel(
            String baseUrl,
            String apiKey,
            String secretKey,
            Double temperature,
            Double topP,
            String modelName,
            String endpoint,
            String responseFormat,
            Double penaltyScore,
            Boolean logRequests,
            Boolean logResponses,
            String userId,
            List<String> stop,
            Integer maxOutputTokens,
            Proxy proxy,
            List<ChatModelListener> listeners) {
        if (isNullOrBlank(apiKey) || isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(
                    " api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }

        this.endpoint = isNullOrBlank(endpoint) ? fromModelName(modelName) : endpoint;
        if (isNullOrBlank(this.endpoint)) {
            throw new IllegalArgumentException(
                    "Qianfan does not have such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.listeners = copy(listeners);
        this.client = QianfanClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://aip.baidubce.com"))
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .proxy(proxy)
                .build();
        this.defaultRequestParameters = ChatRequestParameters.builder()
                .temperature(temperature)
                .topP(topP)
                .stopSequences(stop)
                .modelName(ensureNotNull(modelName, "modelName"))
                .maxOutputTokens(maxOutputTokens)
                .responseFormat("json_object".equals(responseFormat) ? ResponseFormat.JSON : ResponseFormat.TEXT)
                .presencePenalty(penaltyScore)
                .build();

        this.userId = userId;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        ChatRequestParameters parameters = chatRequest.parameters();

        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .messages(InternalQianfanHelper.toOpenAiMessages(messages))
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .maxOutputTokens(parameters.maxOutputTokens())
                .stop(parameters.stopSequences())
                .stream(true)
                .system(getSystemMessage(messages))
                .userId(userId)
                .responseFormat(parameters.responseFormat() == ResponseFormat.JSON ? "json_object" : "text")
                .penaltyScore(parameters.presencePenalty());

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.functions(InternalQianfanHelper.toFunctions(toolSpecifications));
        }

        ChatCompletionRequest request = builder.build();

        QianfanStreamingResponseBuilder responseBuilder = new QianfanStreamingResponseBuilder(null);

        SyncOrAsyncOrStreaming<ChatCompletionResponse> response = client.chatCompletion(request, endpoint);

        response.onPartialResponse(partialResponse -> {
                    try {
                        responseBuilder.append(partialResponse);
                        handle(partialResponse, handler);
                    } catch (Throwable t) {
                        RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(t);
                        withLoggingExceptions(() -> handler.onError(mappedException));
                    }
                })
                .onComplete(() -> {
                    try {
                        ChatResponse chatResponse = responseBuilder.build();
                        handler.onCompleteResponse(chatResponse);
                    } catch (Throwable t) {
                        RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(t);
                        withLoggingExceptions(() -> handler.onError(mappedException));
                    }
                })
                .onError(throwable -> {
                    RuntimeException mappedException = ExceptionMapper.DEFAULT.mapException(throwable);
                    withLoggingExceptions(() -> handler.onError(mappedException));
                })
                .execute();
    }

    private static void handle(ChatCompletionResponse partialResponse, StreamingChatResponseHandler handler) {
        String result = partialResponse.getResult();
        if (Objects.isNull(result) || result.isEmpty()) {
            return;
        }
        handler.onPartialResponse(result);
    }

    public static QianfanStreamingChatModelBuilder builder() {
        for (QianfanStreamingChatModelBuilderFactory factory :
                loadFactories(QianfanStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanStreamingChatModelBuilder();
    }

    public static class QianfanStreamingChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String secretKey;
        private Double temperature;
        private Double topP;
        private String modelName;
        private String endpoint;
        private String responseFormat;
        private Double penaltyScore;
        private Boolean logRequests;
        private Boolean logResponses;
        private String userId;
        private List<String> stop;
        private Integer maxOutputTokens;
        private Proxy proxy;
        private List<ChatModelListener> listeners;

        public QianfanStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QianfanStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QianfanStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QianfanStreamingChatModelBuilder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public QianfanStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public QianfanStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QianfanStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QianfanStreamingChatModelBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public QianfanStreamingChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public QianfanStreamingChatModelBuilder penaltyScore(Double penaltyScore) {
            this.penaltyScore = penaltyScore;
            return this;
        }

        public QianfanStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public QianfanStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public QianfanStreamingChatModelBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public QianfanStreamingChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public QianfanStreamingChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public QianfanStreamingChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public QianfanStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public QianfanStreamingChatModel build() {
            return new QianfanStreamingChatModel(
                    baseUrl,
                    apiKey,
                    secretKey,
                    temperature,
                    topP,
                    modelName,
                    endpoint,
                    responseFormat,
                    penaltyScore,
                    logRequests,
                    logResponses,
                    userId,
                    stop,
                    maxOutputTokens,
                    proxy,
                    listeners);
        }
    }
}
