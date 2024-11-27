package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceLanguageModel;

import java.util.function.Supplier;

public interface XinferenceLanguageModelBuilderFactory extends Supplier<XinferenceLanguageModel.XinferenceLanguageModelBuilder> {
}
