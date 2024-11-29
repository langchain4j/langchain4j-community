package dev.langchain4j.community.model.dashscope.spi;

import dev.langchain4j.community.model.dashscope.QwenStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenStreamingLanguageModel.QwenStreamingLanguageModelBuilder} instances.
 */
public interface QwenStreamingLanguageModelBuilderFactory extends Supplier<QwenStreamingLanguageModel.QwenStreamingLanguageModelBuilder> {
}
