package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceEmbeddingModel;

import java.util.function.Supplier;

public interface XinferenceEmbeddingModelBuilderFactory extends Supplier<XinferenceEmbeddingModel.XinferenceEmbeddingModelBuilder> {
}
