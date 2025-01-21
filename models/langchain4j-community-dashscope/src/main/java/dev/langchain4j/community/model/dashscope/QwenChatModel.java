package dev.langchain4j.community.model.dashscope;

import static com.alibaba.dashscope.aigc.conversation.ConversationParam.ResultFormat.MESSAGE;
import static dev.langchain4j.community.model.dashscope.QwenHelper.aiMessageFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.answerFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.createModelListenerRequest;
import static dev.langchain4j.community.model.dashscope.QwenHelper.finishReasonFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.onListenError;
import static dev.langchain4j.community.model.dashscope.QwenHelper.onListenRequest;
import static dev.langchain4j.community.model.dashscope.QwenHelper.onListenResponse;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toQwenMessages;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toQwenMultiModalMessages;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toToolFunction;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toToolFunctions;
import static dev.langchain4j.community.model.dashscope.QwenHelper.tokenUsageFrom;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;

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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.dashscope.spi.QwenChatModelBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Represents a Qwen language model with a chat completion interface.
 * More details are available <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details">here</a>.
 */
public class QwenChatModel implements ChatLanguageModel {
    private final String apiKey;
    private final String modelName;
    private final Double topP;
    private final Integer topK;
    private final Boolean enableSearch;
    private final Integer seed;
    private final Float repetitionPenalty;
    private final Float temperature;
    private final List<String> stops;
    private final Integer maxTokens;
    private final Generation generation;
    private final MultiModalConversation conv;
    private final boolean isMultimodalModel;
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
            List<ChatModelListener> listeners) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.QWEN_PLUS : modelName;
        this.enableSearch = enableSearch != null && enableSearch;
        this.apiKey = apiKey;
        this.topP = topP;
        this.topK = topK;
        this.seed = seed;
        this.repetitionPenalty = repetitionPenalty;
        this.temperature = temperature;
        this.stops = stops;
        this.maxTokens = maxTokens;
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.isMultimodalModel = QwenHelper.isMultimodalModel(this.modelName);

        if (Utils.isNullOrBlank(baseUrl)) {
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
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return isMultimodalModel
                ? generateByMultimodalModel(messages, null, null)
                : generateByNonMultimodalModel(messages, null, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return isMultimodalModel
                ? generateByMultimodalModel(messages, toolSpecifications, null)
                : generateByNonMultimodalModel(messages, toolSpecifications, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return isMultimodalModel
                ? generateByMultimodalModel(messages, null, toolSpecification)
                : generateByNonMultimodalModel(messages, null, toolSpecification);
    }

    private Response<AiMessage> generateByNonMultimodalModel(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted) {

        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .topP(topP)
                .topK(topK)
                .enableSearch(enableSearch)
                .seed(seed)
                .repetitionPenalty(repetitionPenalty)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .messages(toQwenMessages(messages))
                .resultFormat(MESSAGE);

        if (stops != null) {
            builder.stopStrings(stops);
        }

        if (!isNullOrEmpty(toolSpecifications)) {
            builder.tools(toToolFunctions(toolSpecifications));
        } else if (toolThatMustBeExecuted != null) {
            builder.tools(toToolFunctions(Collections.singleton(toolThatMustBeExecuted)));
            builder.toolChoice(toToolFunction(toolThatMustBeExecuted));
        }

        generationParamCustomizer.accept(builder);
        GenerationParam param = builder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(param, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        onListenRequest(listeners, modelListenerRequest, attributes);

        try {
            GenerationResult result = generation.call(param);
            Response<AiMessage> response =
                    Response.from(aiMessageFrom(result), tokenUsageFrom(result), finishReasonFrom(result));

            onListenResponse(listeners, result.getRequestId(), response, modelListenerRequest, attributes);
            return response;
        } catch (NoApiKeyException | InputRequiredException e) {
            onListenError(listeners, null, e, modelListenerRequest, null, attributes);
            throw new IllegalArgumentException(e);
        } catch (RuntimeException e) {
            onListenError(listeners, null, e, modelListenerRequest, null, attributes);
            throw e;
        }
    }

    private Response<AiMessage> generateByMultimodalModel(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted) {
        if (toolThatMustBeExecuted != null || !isNullOrEmpty(toolSpecifications)) {
            throw new IllegalArgumentException("Tools are currently not supported by this model");
        }

        MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                MultiModalConversationParam.builder()
                        .apiKey(apiKey)
                        .model(modelName)
                        .topP(topP)
                        .topK(topK)
                        .enableSearch(enableSearch)
                        .seed(seed)
                        .temperature(temperature)
                        .maxLength(maxTokens)
                        .messages(toQwenMultiModalMessages(messages));

        multimodalConversationParamCustomizer.accept(builder);
        MultiModalConversationParam param = builder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(param, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        onListenRequest(listeners, modelListenerRequest, attributes);

        try {
            MultiModalConversationResult result = conv.call(param);
            String answer = answerFrom(result);

            Response<AiMessage> response =
                    Response.from(AiMessage.from(answer), tokenUsageFrom(result), finishReasonFrom(result));

            onListenResponse(listeners, result.getRequestId(), response, modelListenerRequest, attributes);
            return response;
        } catch (NoApiKeyException e) {
            onListenError(listeners, null, e, modelListenerRequest, null, attributes);
            throw new IllegalArgumentException(e);
        } catch (UploadFileException e) {
            onListenError(listeners, null, e, modelListenerRequest, null, attributes);
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            onListenError(listeners, null, e, modelListenerRequest, null, attributes);
            throw e;
        }
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
                    listeners);
        }
    }
}
