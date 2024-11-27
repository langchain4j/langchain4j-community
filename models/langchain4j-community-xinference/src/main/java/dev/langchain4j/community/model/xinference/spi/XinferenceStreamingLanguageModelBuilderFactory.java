package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceStreamingLanguageModel;

import java.util.function.Supplier;

public interface XinferenceStreamingLanguageModelBuilderFactory extends Supplier<XinferenceStreamingLanguageModel.XinferenceStreamingLanguageModelBuilder> {
}
