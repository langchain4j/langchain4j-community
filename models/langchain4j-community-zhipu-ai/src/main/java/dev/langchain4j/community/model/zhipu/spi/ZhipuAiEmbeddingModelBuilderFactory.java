package dev.langchain4j.community.model.zhipu.spi;

import dev.langchain4j.community.model.zhipu.ZhipuAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link ZhipuAiEmbeddingModel.ZhipuAiEmbeddingModelBuilder} instances.
 */
public interface ZhipuAiEmbeddingModelBuilderFactory extends Supplier<ZhipuAiEmbeddingModel.ZhipuAiEmbeddingModelBuilder> {
}
