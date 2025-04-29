package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenHelper.toQwenMessages;
import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_PLUS;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tokenizers.Tokenization;
import com.alibaba.dashscope.tokenizers.TokenizationResult;
import dev.langchain4j.community.model.dashscope.spi.QwenTokenizerBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

public class QwenTokenCountEstimator implements TokenCountEstimator {

    private final String apiKey;
    private final String modelName;
    private final Tokenization tokenizer;
    private Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer = p -> {};

    public QwenTokenCountEstimator(String apiKey, String modelName) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. Reference: https://www.alibabacloud.com/help/en/model-studio/get-api-key");
        }
        this.apiKey = apiKey;
        this.modelName = getOrDefault(modelName, QWEN_PLUS);
        this.tokenizer = new Tokenization();
    }

    @Override
    public int estimateTokenCountInText(String text) {
        String prompt = isBlank(text) ? text + "_" : text;
        try {
            GenerationParam.GenerationParamBuilder<?, ?> builder =
                    GenerationParam.builder().apiKey(apiKey).model(modelName).prompt(prompt);

            generationParamCustomizer.accept(builder);
            TokenizationResult result = tokenizer.call(builder.build());
            int tokenCount = result.getUsage().getInputTokens();
            return Objects.equals(prompt, text) ? tokenCount : tokenCount - 1;
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(Collections.singleton(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (isNullOrEmpty(messages)) {
            return 0;
        }

        GenerationParam.GenerationParamBuilder<?, ?> builder =
                GenerationParam.builder().apiKey(apiKey).model(modelName).messages(toQwenMessages(messages));

        try {
            generationParamCustomizer.accept(builder);
            TokenizationResult result = tokenizer.call(builder.build());
            return result.getUsage().getInputTokens();
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean isBlank(CharSequence cs) {
        int strLen = cs == null ? 0 : cs.length();
        for (int i = 0; i < strLen; ++i) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public void setGenerationParamCustomizer(
            Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer) {
        this.generationParamCustomizer = ensureNotNull(generationParamCustomizer, "generationParamConsumer");
    }

    public static QwenTokenizerBuilder builder() {
        for (QwenTokenizerBuilderFactory factory : loadFactories(QwenTokenizerBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenTokenizerBuilder();
    }

    public static class QwenTokenizerBuilder {

        private String apiKey;
        private String modelName;

        public QwenTokenizerBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenTokenizerBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenTokenizerBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenTokenCountEstimator build() {
            return new QwenTokenCountEstimator(apiKey, modelName);
        }
    }
}
