package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.getSystemMessage;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.qianfan.client.QianfanClient;
import dev.langchain4j.community.model.qianfan.client.SyncOrAsyncOrStreaming;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.qianfan.spi.QianfanStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
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
    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final String modelName;
    private final String endpoint;
    private final Double penaltyScore;
    private final String responseFormat;

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
            Proxy proxy) {
        if (Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(
                    " api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }
        this.modelName = modelName;
        this.endpoint = Utils.isNullOrBlank(endpoint) ? QianfanChatModelNameEnum.fromModelName(modelName) : endpoint;

        if (Utils.isNullOrBlank(this.endpoint)) {
            throw new IllegalArgumentException(
                    "Qianfan is no such model name(or there is no model definition in the QianfanChatModelNameEnum class). You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl, "https://aip.baidubce.com");
        this.client = QianfanClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .proxy(proxy)
                .build();
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.penaltyScore = penaltyScore;
        this.responseFormat = responseFormat;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        generate(chatRequest.messages(), chatRequest.toolSpecifications(), handler);
    }

    private void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingChatResponseHandler handler) {
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .messages(InternalQianfanHelper.toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .system(getSystemMessage(messages))
                .responseFormat(responseFormat)
                .penaltyScore(penaltyScore);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.functions(InternalQianfanHelper.toFunctions(toolSpecifications));
        }

        ChatCompletionRequest request = builder.build();

        QianfanStreamingResponseBuilder responseBuilder = new QianfanStreamingResponseBuilder(null);

        SyncOrAsyncOrStreaming<ChatCompletionResponse> response = client.chatCompletion(request, endpoint);

        response.onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    handle(partialResponse, handler);
                })
                .onComplete(() -> {
                    ChatResponse chatResponse = responseBuilder.build();
                    handler.onCompleteResponse(chatResponse);
                })
                .onError(handler::onError)
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
        private Proxy proxy;

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

        public QianfanStreamingChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
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
                    proxy);
        }
    }
}
