package dev.langchain4j.community.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.dashscope.spi.QwenStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.community.model.dashscope.QwenHelper.convertHandler;
import static dev.langchain4j.community.model.dashscope.QwenHelper.isMultimodalModel;
import static dev.langchain4j.community.model.dashscope.QwenHelper.repetitionPenaltyToFrequencyPenalty;
import static dev.langchain4j.community.model.dashscope.QwenHelper.supportIncrementalOutput;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toGenerationParam;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toMultiModalConversationParam;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;

/**
 * Represents a Qwen language model with a chat completion interface.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details">here</a>
 */
public class QwenStreamingChatModel implements StreamingChatLanguageModel {
    private final QwenChatRequestParameters defaultRequestParameters;
    private final String apiKey;
    private final Generation generation;
    private final MultiModalConversation conv;
    private final boolean isMultimodalModel;
    private final List<ChatModelListener> listeners;
    private Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer = p -> {};
    private Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>>
            multimodalConversationParamCustomizer = p -> {};

    public QwenStreamingChatModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double topP,
            Integer topK,
            Boolean enableSearch,
            Integer seed,
            Float repetitionPenalty,
            Float temperature,
            List<String> stops,
            Integer maxTokens,
            List<ChatModelListener> listeners,
            ChatRequestParameters defaultRequestParameters) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }

        ChatRequestParameters commonParameters;
        if (defaultRequestParameters != null) {
            commonParameters = defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.builder().build();
        }

        QwenChatRequestParameters qwenParameters;
        if (defaultRequestParameters instanceof QwenChatRequestParameters qwenChatRequestParameters) {
            qwenParameters = qwenChatRequestParameters;
        } else {
            qwenParameters = QwenChatRequestParameters.builder().build();
        }

        Double temperatureParameter = isNull(temperature) ? null : temperature.doubleValue();
        Double frequencyPenaltyParameter = repetitionPenaltyToFrequencyPenalty(repetitionPenalty);
        String modelNameParameter = getOrDefault(modelName, commonParameters.modelName());

        this.apiKey = apiKey;
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.isMultimodalModel = isMultimodalModel(modelNameParameter);
        this.defaultRequestParameters = QwenChatRequestParameters.builder()
                // common parameters
                .modelName(modelNameParameter)
                .temperature(getOrDefault(temperatureParameter, commonParameters.temperature()))
                .topP(getOrDefault(topP, commonParameters.topP()))
                .topK(getOrDefault(topK, commonParameters.topK()))
                .frequencyPenalty(getOrDefault(frequencyPenaltyParameter, commonParameters.frequencyPenalty()))
                .presencePenalty(commonParameters.presencePenalty())
                .maxOutputTokens(getOrDefault(maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(stops, () -> copyIfNotNull(commonParameters.stopSequences())))
                .toolSpecifications(copyIfNotNull(commonParameters.toolSpecifications()))
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(commonParameters.responseFormat())
                // Qwen-specific parameters
                .seed(getOrDefault(seed, qwenParameters.seed()))
                .enableSearch(getOrDefault(enableSearch, qwenParameters.enableSearch()))
                .searchOptions(qwenParameters.searchOptions())
                .translationOptions(qwenParameters.translationOptions())
                .vlHighResolutionImages(qwenParameters.vlHighResolutionImages())
                .custom(copyIfNotNull(qwenParameters.custom()))
                .build();

        if (isNullOrBlank(baseUrl)) {
            this.conv = isMultimodalModel ? new MultiModalConversation() : null;
            this.generation = isMultimodalModel ? null : new Generation();
        } else if (baseUrl.startsWith("wss://")) {
            this.conv = isMultimodalModel ? new MultiModalConversation(Protocol.WEBSOCKET.getValue(), baseUrl) : null;
            this.generation = isMultimodalModel ? null : new Generation(Protocol.WEBSOCKET.getValue(), baseUrl);
        } else {
            this.conv = isMultimodalModel ? new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl) : null;
            this.generation = isMultimodalModel ? null : new Generation(Protocol.HTTP.getValue(), baseUrl);
        }
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void generate(
            List<ChatMessage> messages,
            ToolSpecification toolSpecification,
            StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();
        chat(chatRequest, convertHandler(handler));
    }

    private void generateByNonMultimodalModel(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        boolean incrementalOutput = supportIncrementalOutput(chatRequest.parameters().modelName());
        GenerationParam param = toGenerationParam(apiKey, chatRequest, generationParamCustomizer, incrementalOutput);
        QwenStreamingResponseBuilder responseBuilder = new QwenStreamingResponseBuilder(param.getModel(), incrementalOutput);
        try {
            generation.streamCall(param, new ResultCallback<>() {
                @Override
                public void onEvent(GenerationResult result) {
                    String delta = responseBuilder.append(result);
                    if (isNotNullOrEmpty(delta)) {
                        handler.onPartialResponse(delta);
                    }
                }

                @Override
                public void onComplete() {
                    handler.onCompleteResponse(responseBuilder.build());
                }

                @Override
                public void onError(Exception e) {
                    handler.onError(e);
                }
            });
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void generateByMultimodalModel(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        boolean incrementalOutput = supportIncrementalOutput(chatRequest.parameters().modelName());
        MultiModalConversationParam param =
                toMultiModalConversationParam(apiKey, chatRequest, multimodalConversationParamCustomizer, incrementalOutput);
        QwenStreamingResponseBuilder responseBuilder = new QwenStreamingResponseBuilder(param.getModel(), incrementalOutput);
        try {
            conv.streamCall(param, new ResultCallback<>() {
                @Override
                public void onEvent(MultiModalConversationResult result) {
                    String delta = responseBuilder.append(result);
                    if (isNotNullOrEmpty(delta)) {
                        handler.onPartialResponse(delta);
                    }
                }

                @Override
                public void onComplete() {
                    handler.onCompleteResponse(responseBuilder.build());
                }

                @Override
                public void onError(Exception e) {
                    handler.onError(e);
                }
            });
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new IllegalArgumentException(e);
        } catch (UploadFileException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        if (isMultimodalModel) {
            generateByMultimodalModel(chatRequest, handler);
        } else {
            generateByNonMultimodalModel(chatRequest, handler);
        }
    }

    @Override
    public QwenChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    public void setGenerationParamCustomizer(
            Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer) {
        this.generationParamCustomizer = ensureNotNull(generationParamCustomizer, "generationParamConsumer");
    }

    public void setMultimodalConversationParamCustomizer(
            Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>>
                    multimodalConversationParamCustomizer) {
        this.multimodalConversationParamCustomizer =
                ensureNotNull(multimodalConversationParamCustomizer, "multimodalConversationParamCustomizer");
    }

    public static QwenStreamingChatModelBuilder builder() {
        for (QwenStreamingChatModelBuilderFactory factory : loadFactories(QwenStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenStreamingChatModelBuilder();
    }

    public static class QwenStreamingChatModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double topP;
        private Integer topK;
        private Boolean enableSearch;
        private Integer seed;
        private Float repetitionPenalty;
        private Float temperature;
        private List<String> stops;
        private Integer maxTokens;
        private List<ChatModelListener> listeners;
        private ChatRequestParameters defaultRequestParameters;

        public QwenStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QwenStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QwenStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public QwenStreamingChatModelBuilder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public QwenStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public QwenStreamingChatModelBuilder repetitionPenalty(Float repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public QwenStreamingChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public QwenStreamingChatModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public QwenStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public QwenStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public QwenStreamingChatModelBuilder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public QwenStreamingChatModel build() {
            return new QwenStreamingChatModel(
                    baseUrl,
                    apiKey,
                    modelName,
                    topP,
                    topK,
                    enableSearch,
                    seed,
                    repetitionPenalty,
                    temperature,
                    stops,
                    maxTokens,
                    listeners,
                    defaultRequestParameters);
        }

        @Override
        public String toString() {
            return "QwenStreamingChatModelBuilder{" + "baseUrl="
                    + quoted(baseUrl) + ", modelName='"
                    + quoted(modelName) + ", topP="
                    + topP + ", topK="
                    + topK + ", enableSearch="
                    + enableSearch + ", seed="
                    + seed + ", repetitionPenalty="
                    + repetitionPenalty + ", temperature="
                    + temperature + ", stops="
                    + stops + ", maxTokens="
                    + maxTokens + ", listeners="
                    + listeners + ", defaultRequestParameters="
                    + defaultRequestParameters + '}';
        }
    }
}
