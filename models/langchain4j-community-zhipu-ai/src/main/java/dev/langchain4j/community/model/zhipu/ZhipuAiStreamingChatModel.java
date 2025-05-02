package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toTools;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toZhipuAiMessages;
import static dev.langchain4j.community.model.zhipu.chat.ToolChoiceMode.AUTO;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;

public class ZhipuAiStreamingChatModel implements StreamingChatModel {

    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    public ZhipuAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            List<String> stops,
            String model,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout) {
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
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = request.messages();
        List<ToolSpecification> toolSpecifications = request.toolSpecifications();

        ChatRequestParameters parameters = request.parameters();
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(parameters.modelName())
                .messages(toZhipuAiMessages(messages))
                .maxTokens(parameters.maxOutputTokens())
                .stop(parameters.stopSequences())
                .stream(true)
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .toolChoice(AUTO);

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ChatCompletionRequest completionRequest = requestBuilder.build();

        client.streamingChatCompletion(completionRequest, handler);
    }

    public static ZhipuAiStreamingChatModelBuilder builder() {
        for (ZhipuAiStreamingChatModelBuilderFactory factories :
                loadFactories(ZhipuAiStreamingChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiStreamingChatModelBuilder();
    }

    public static class ZhipuAiStreamingChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private Double temperature;
        private Double topP;
        private List<String> stops;
        private String model;
        private Integer maxToken;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        public ZhipuAiStreamingChatModelBuilder model(ChatCompletionModel model) {
            this.model = model.toString();
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder maxToken(Integer maxToken) {
            this.maxToken = maxToken;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        /**
         * @deprecated This method is deprecated due to {@link ZhipuAiClient} use {@link dev.langchain4j.http.client.HttpClient} as an http client.
         */
        @Deprecated(since = "1.0.0-beta4", forRemoval = true)
        public ZhipuAiStreamingChatModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * @deprecated This method is deprecated due to {@link ZhipuAiClient} use {@link dev.langchain4j.http.client.HttpClient} as an http client.
         */
        @Deprecated(since = "1.0.0-beta4", forRemoval = true)
        public ZhipuAiStreamingChatModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiStreamingChatModel build() {
            return new ZhipuAiStreamingChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.temperature,
                    this.topP,
                    this.stops,
                    this.model,
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
