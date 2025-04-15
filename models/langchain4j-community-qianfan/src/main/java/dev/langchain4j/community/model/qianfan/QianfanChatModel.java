package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.aiMessageFrom;
import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.finishReasonFrom;
import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.getSystemMessage;
import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.toFunctions;
import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.toOpenAiMessages;
import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.qianfan.client.QianfanClient;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.qianfan.spi.QianfanChatModelBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.util.List;

/**
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class QianfanChatModel implements ChatModel {

    private final QianfanClient client;
    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final String modelName;
    private final String endpoint;
    private final Double penaltyScore;
    private final Integer maxRetries;
    private final String responseFormat;
    private final String userId;
    private final List<String> stop;
    private final Integer maxOutputTokens;
    private final String system;

    public QianfanChatModel(
            String baseUrl,
            String apiKey,
            String secretKey,
            Double temperature,
            Integer maxRetries,
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
            String system,
            Proxy proxy) {
        if (Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(
                    " api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }

        this.modelName = modelName;
        this.endpoint = Utils.isNullOrBlank(endpoint) ? QianfanChatModelNameEnum.fromModelName(modelName) : endpoint;

        if (Utils.isNullOrBlank(this.endpoint)) {
            throw new IllegalArgumentException(
                    "Qianfan is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl, "https://aip.baidubce.com");

        this.client = QianfanClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .proxy(proxy)
                .build();
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.penaltyScore = penaltyScore;
        this.responseFormat = responseFormat;
        this.maxOutputTokens = maxOutputTokens;
        this.stop = stop;
        this.userId = userId;
        this.system = system;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        Response<AiMessage> response = doChat(chatRequest.messages(), chatRequest.toolSpecifications());
        return ChatResponse.builder()
                .aiMessage(response.content())
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .build();
    }

    private Response<AiMessage> doChat(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .maxOutputTokens(maxOutputTokens)
                .stop(stop)
                .system(system)
                .userId(userId)
                .penaltyScore(penaltyScore)
                .responseFormat(responseFormat);
        if (system == null || system.isEmpty()) {
            builder.system(getSystemMessage(messages));
        }

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.functions(toFunctions(toolSpecifications));
        }

        ChatCompletionRequest param = builder.build();

        ChatCompletionResponse response =
                withRetry(() -> client.chatCompletion(param, endpoint).execute(), maxRetries);

        return Response.from(
                aiMessageFrom(response), tokenUsageFrom(response), finishReasonFrom(response.getFinishReason()));
    }

    public static QianfanChatModelBuilder builder() {
        for (QianfanChatModelBuilderFactory factory : loadFactories(QianfanChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanChatModelBuilder();
    }

    public static class QianfanChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String secretKey;
        private Double temperature;
        private Integer maxRetries;
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
        private String system;
        private Proxy proxy;

        public QianfanChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QianfanChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QianfanChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QianfanChatModelBuilder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public QianfanChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public QianfanChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public QianfanChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QianfanChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QianfanChatModelBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public QianfanChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public QianfanChatModelBuilder penaltyScore(Double penaltyScore) {
            this.penaltyScore = penaltyScore;
            return this;
        }

        public QianfanChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public QianfanChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public QianfanChatModelBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public QianfanChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public QianfanChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public QianfanChatModelBuilder system(String system) {
            this.system = system;
            return this;
        }

        public QianfanChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public QianfanChatModel build() {
            return new QianfanChatModel(
                    baseUrl,
                    apiKey,
                    secretKey,
                    temperature,
                    maxRetries,
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
                    system,
                    proxy);
        }
    }
}
