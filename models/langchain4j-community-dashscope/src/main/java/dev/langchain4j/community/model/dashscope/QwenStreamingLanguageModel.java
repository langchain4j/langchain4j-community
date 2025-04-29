package dev.langchain4j.community.model.dashscope;

import static com.alibaba.dashscope.aigc.generation.GenerationParam.ResultFormat.MESSAGE;
import static dev.langchain4j.community.model.dashscope.QwenHelper.isSupportingIncrementalOutputModelName;
import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_PLUS;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.community.model.dashscope.spi.QwenStreamingLanguageModelBuilderFactory;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a Qwen language model with a text interface.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api">here</a>.
 */
public class QwenStreamingLanguageModel implements StreamingLanguageModel {

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
    private Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer = p -> {};

    public QwenStreamingLanguageModel(
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
            Integer maxTokens) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. Reference: https://www.alibabacloud.com/help/en/model-studio/get-api-key");
        }
        this.modelName = isNullOrBlank(modelName) ? QWEN_PLUS : modelName;
        this.enableSearch = enableSearch != null && enableSearch;
        this.apiKey = apiKey;
        this.topP = topP;
        this.topK = topK;
        this.seed = seed;
        this.repetitionPenalty = repetitionPenalty;
        this.temperature = temperature;
        this.stops = stops;
        this.maxTokens = maxTokens;

        if (Utils.isNullOrBlank(baseUrl)) {
            this.generation = new Generation();
        } else if (baseUrl.startsWith("wss://")) {
            this.generation = new Generation(Protocol.WEBSOCKET.getValue(), baseUrl);
        } else {
            this.generation = new Generation(Protocol.HTTP.getValue(), baseUrl);
        }
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        boolean incrementalOutput = isSupportingIncrementalOutputModelName(modelName);
        try {
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
                    .incrementalOutput(incrementalOutput)
                    .prompt(prompt)
                    .resultFormat(MESSAGE);

            if (stops != null) {
                builder.stopStrings(stops);
            }

            generationParamCustomizer.accept(builder);

            QwenStreamingResponseBuilder responseBuilder =
                    new QwenStreamingResponseBuilder(modelName, incrementalOutput);
            generation.streamCall(builder.build(), new ResultCallback<>() {
                @Override
                public void onEvent(GenerationResult result) {
                    String delta = responseBuilder.append(result);
                    if (Utils.isNotNullOrBlank(delta)) {
                        handler.onNext(delta);
                    }
                }

                @Override
                public void onComplete() {
                    ChatResponse response = responseBuilder.build();
                    handler.onComplete(
                            Response.from(response.aiMessage().text(), response.tokenUsage(), response.finishReason()));
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

    public void setGenerationParamCustomizer(
            Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer) {
        this.generationParamCustomizer = ensureNotNull(generationParamCustomizer, "generationParamConsumer");
    }

    public static QwenStreamingLanguageModelBuilder builder() {
        for (QwenStreamingLanguageModelBuilderFactory factory :
                loadFactories(QwenStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenStreamingLanguageModelBuilder();
    }

    public static class QwenStreamingLanguageModelBuilder {

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

        public QwenStreamingLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QwenStreamingLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenStreamingLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QwenStreamingLanguageModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public QwenStreamingLanguageModelBuilder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public QwenStreamingLanguageModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public QwenStreamingLanguageModelBuilder repetitionPenalty(Float repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public QwenStreamingLanguageModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public QwenStreamingLanguageModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public QwenStreamingLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public QwenStreamingLanguageModel build() {
            return new QwenStreamingLanguageModel(
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
                    maxTokens);
        }
    }
}
