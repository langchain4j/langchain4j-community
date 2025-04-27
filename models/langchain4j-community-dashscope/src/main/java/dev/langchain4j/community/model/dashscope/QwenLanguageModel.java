package dev.langchain4j.community.model.dashscope;

import static com.alibaba.dashscope.aigc.generation.GenerationParam.ResultFormat.MESSAGE;
import static dev.langchain4j.community.model.dashscope.QwenHelper.answerFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.finishReasonFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.tokenUsageFrom;
import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_PLUS;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import dev.langchain4j.community.model.dashscope.spi.QwenLanguageModelBuilderFactory;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a Qwen language model with a text interface.
 * More details are available <a href="https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api">here</a>.
 */
public class QwenLanguageModel implements LanguageModel {
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

    public QwenLanguageModel(
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
    public Response<String> generate(String prompt) {
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
                    .prompt(prompt)
                    .resultFormat(MESSAGE);

            if (stops != null) {
                builder.stopStrings(stops);
            }

            generationParamCustomizer.accept(builder);
            GenerationResult generationResult = generation.call(builder.build());

            return Response.from(
                    answerFrom(generationResult), tokenUsageFrom(generationResult), finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void setGenerationParamCustomizer(
            Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer) {
        this.generationParamCustomizer = ensureNotNull(generationParamCustomizer, "generationParamConsumer");
    }

    public static QwenLanguageModelBuilder builder() {
        for (QwenLanguageModelBuilderFactory factory : loadFactories(QwenLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenLanguageModelBuilder();
    }

    public static class QwenLanguageModelBuilder {
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

        public QwenLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenLanguageModel.QwenLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder repetitionPenalty(Float repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public QwenLanguageModel.QwenLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public QwenLanguageModel build() {
            return new QwenLanguageModel(
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
