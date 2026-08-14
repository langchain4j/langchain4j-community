package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toToolChoice;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toTools;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toZhipuAiMessages;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toZhipuResponseFormat;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.zhipu.chat.Thinking;
import dev.langchain4j.community.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;

public class ZhipuAiStreamingChatModel implements StreamingChatModel {

    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;
    private final ZhipuAiChatRequestParameters defaultRequestParameters;

    public ZhipuAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            List<String> stops,
            ResponseFormat responseFormat,
            String model,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners,
            Boolean doSample,
            Boolean toolStream,
            Thinking thinking,
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

        this.defaultRequestParameters = ZhipuAiChatRequestParameters.builder()
                .modelName(ensureNotNull(model, "model"))
                .temperature(temperature)
                .topP(topP)
                .stopSequences(stops)
                .responseFormat(responseFormat)
                .maxOutputTokens(maxToken)
                .doSample(doSample)
                .toolStream(toolStream)
                .thinking(thinking)
                .build();
    }

    @Override
    public ZhipuAiChatRequestParameters defaultRequestParameters() {
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
                .responseFormat(toZhipuResponseFormat(request.responseFormat()))
                .stream(true)
                .temperature(parameters.temperature())
                .topP(parameters.topP());

        if (parameters instanceof ZhipuAiChatRequestParameters zp) {
            requestBuilder.doSample(zp.doSample());
            requestBuilder.toolStream(zp.toolStream());
            requestBuilder.thinking(zp.thinking());
        }

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
            requestBuilder.toolChoice(toToolChoice(parameters.toolChoice()));
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
        private ResponseFormat responseFormat;
        private String model;
        private Integer maxToken;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Boolean doSample;
        private Boolean toolStream;
        private Thinking thinking;
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

        public ZhipuAiStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
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

        public ZhipuAiStreamingChatModelBuilder doSample(Boolean doSample) {
            this.doSample = doSample;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder toolStream(Boolean toolStream) {
            this.toolStream = toolStream;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder thinking(Thinking thinking) {
            this.thinking = thinking;
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
                    this.responseFormat,
                    this.model,
                    this.maxToken,
                    this.logRequests,
                    this.logResponses,
                    this.listeners,
                    this.doSample,
                    this.toolStream,
                    this.thinking,
                    this.callTimeout,
                    this.connectTimeout,
                    this.readTimeout,
                    this.writeTimeout);
        }
    }
}
