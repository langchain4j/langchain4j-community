package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.aiMessageFrom;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.finishReasonFrom;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.isSuccessFinishReason;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toTools;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toZhipuAiMessages;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.tokenUsageFrom;
import static dev.langchain4j.community.model.zhipu.chat.ToolChoiceMode.AUTO;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.zhipu.spi.ZhipuAiChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.time.Duration;
import java.util.List;

/**
 * Represents an ZhipuAi language model with a chat completion interface, such as glm-3-turbo and glm-4.
 * You can find description of parameters <a href="https://open.bigmodel.cn/dev/api">here</a>.
 */
public class ZhipuAiChatModel implements ChatModel {

    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;
    private final Integer maxRetries;

    private final ChatRequestParameters defaultRequestParameters;

    public ZhipuAiChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            String model,
            List<String> stops,
            Integer maxRetries,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout) {
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = copy(listeners);
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.defaultRequestParameters = ChatRequestParameters.builder()
                .temperature(getOrDefault(temperature, 0.7))
                .topP(topP)
                .stopSequences(stops)
                .modelName(ensureNotNull(model, "model"))
                .maxOutputTokens(getOrDefault(maxToken, 512))
                .build();
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
    public ChatResponse doChat(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        List<ToolSpecification> toolSpecifications = request.toolSpecifications();
        ChatRequestParameters parameters = request.parameters();
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(parameters.modelName())
                .messages(toZhipuAiMessages(messages))
                .maxTokens(parameters.maxOutputTokens())
                .stop(parameters.stopSequences())
                .stream(false)
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .toolChoice(AUTO);

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ChatCompletionRequest completionRequest = requestBuilder.build();

        ChatCompletionResponse completionResponse =
                withRetry(() -> client.chatCompletion(completionRequest), maxRetries);

        FinishReason finishReason =
                finishReasonFrom(completionResponse.getChoices().get(0).getFinishReason());

        ChatResponse response = ChatResponse.builder()
                .aiMessage(aiMessageFrom(completionResponse))
                .tokenUsage(tokenUsageFrom(completionResponse.getUsage()))
                .finishReason(finishReason)
                .id(completionResponse.getId())
                .modelName(completionResponse.getModel())
                .build();

        if (!isSuccessFinishReason(finishReason)) {
            throw new ZhipuAiException(response.aiMessage().text());
        }
        return response;
    }

    public static ZhipuAiChatModelBuilder builder() {
        for (ZhipuAiChatModelBuilderFactory factories : loadFactories(ZhipuAiChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiChatModelBuilder();
    }

    public static class ZhipuAiChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private Double temperature;
        private Double topP;
        private String model;
        private List<String> stops;
        private Integer maxRetries;
        private Integer maxToken;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        public ZhipuAiChatModelBuilder() {}

        public ZhipuAiChatModelBuilder model(ChatCompletionModel model) {
            this.model = model.toString();
            return this;
        }

        public ZhipuAiChatModelBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ZhipuAiChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ZhipuAiChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ZhipuAiChatModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public ZhipuAiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ZhipuAiChatModelBuilder maxToken(Integer maxToken) {
            this.maxToken = maxToken;
            return this;
        }

        public ZhipuAiChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * @deprecated This method is deprecated due to {@link ZhipuAiClient} use {@link dev.langchain4j.http.client.HttpClient} as an http client.
         */
        @Deprecated(since = "1.0.0-beta4", forRemoval = true)
        public ZhipuAiChatModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAiChatModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAiChatModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * @deprecated This method is deprecated due to {@link ZhipuAiClient} use {@link dev.langchain4j.http.client.HttpClient} as an http client.
         */
        @Deprecated(since = "1.0.0-beta4", forRemoval = true)
        public ZhipuAiChatModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiChatModel build() {
            return new ZhipuAiChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.temperature,
                    this.topP,
                    this.model,
                    this.stops,
                    this.maxRetries,
                    this.maxToken,
                    this.logRequests,
                    this.logResponses,
                    this.listeners,
                    this.callTimeout,
                    this.connectTimeout,
                    this.readTimeout,
                    this.writeTimeout);
        }
    }
}
