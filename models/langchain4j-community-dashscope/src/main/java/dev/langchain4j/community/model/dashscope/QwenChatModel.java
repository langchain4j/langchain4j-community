package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenHelper.chatResponseFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.isMultimodalModel;
import static dev.langchain4j.community.model.dashscope.QwenHelper.repetitionPenaltyToFrequencyPenalty;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toGenerationParam;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toMultiModalConversationParam;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.community.model.dashscope.spi.QwenChatModelBuilderFactory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a Qwen language model with a chat completion interface.
 * More details are available <a href="https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api">here</a>.
 */
public class QwenChatModel implements ChatModel {
    private final QwenChatRequestParameters defaultRequestParameters;
    private final String apiKey;
    private final Generation generation;
    private final MultiModalConversation conv;
    private final List<ChatModelListener> listeners;
    private Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer = p -> {};
    private Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>>
            multimodalConversationParamCustomizer = p -> {};

    protected QwenChatModel(
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
            ChatRequestParameters defaultRequestParameters,
            Boolean isMultimodalModel) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. Reference: https://www.alibabacloud.com/help/en/model-studio/get-api-key");
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
                .isMultimodalModel(getOrDefault(isMultimodalModel, qwenParameters.isMultimodalModel()))
                .supportIncrementalOutput(qwenParameters.supportIncrementalOutput())
                .enableThinking(qwenParameters.enableThinking())
                .thinkingBudget(qwenParameters.thinkingBudget())
                .custom(copyIfNotNull(qwenParameters.custom()))
                .build();

        if (isNullOrBlank(baseUrl)) {
            this.conv = new MultiModalConversation();
            this.generation = new Generation();
        } else if (baseUrl.startsWith("wss://")) {
            this.conv = new MultiModalConversation(Protocol.WEBSOCKET.getValue(), baseUrl);
            this.generation = new Generation(Protocol.WEBSOCKET.getValue(), baseUrl);
        } else {
            this.conv = new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl);
            this.generation = new Generation(Protocol.HTTP.getValue(), baseUrl);
        }
    }

    private ChatResponse generateByNonMultimodalModel(ChatRequest chatRequest) {
        GenerationParam param = toGenerationParam(apiKey, chatRequest, generationParamCustomizer, false);
        try {
            GenerationResult result = generation.call(param);
            return chatResponseFrom(param.getModel(), result);
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private ChatResponse generateByMultimodalModel(ChatRequest chatRequest) {
        MultiModalConversationParam param =
                toMultiModalConversationParam(apiKey, chatRequest, multimodalConversationParamCustomizer, false);
        try {
            MultiModalConversationResult result = conv.call(param);
            return chatResponseFrom(param.getModel(), result);
        } catch (NoApiKeyException e) {
            throw new IllegalArgumentException(e);
        } catch (UploadFileException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return isMultimodalModel(chatRequest)
                ? generateByMultimodalModel(chatRequest)
                : generateByNonMultimodalModel(chatRequest);
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

    public static QwenChatModelBuilder builder() {
        for (QwenChatModelBuilderFactory factory : loadFactories(QwenChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenChatModelBuilder();
    }

    public static class QwenChatModelBuilder {
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
        private Boolean isMultimodalModel;

        public QwenChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QwenChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QwenChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public QwenChatModelBuilder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public QwenChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public QwenChatModelBuilder repetitionPenalty(Float repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public QwenChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public QwenChatModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public QwenChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public QwenChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public QwenChatModelBuilder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public QwenChatModelBuilder isMultimodalModel(Boolean isMultimodalModel) {
            this.isMultimodalModel = isMultimodalModel;
            return this;
        }

        public QwenChatModel build() {
            return new QwenChatModel(
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
                    defaultRequestParameters,
                    isMultimodalModel);
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
