package dev.langchain4j.community.model.qianfan.spi;


import dev.langchain4j.community.model.qianfan.QianfanEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanEmbeddingModel.QianfanEmbeddingModelBuilder} instances.
 */
public interface QianfanEmbeddingModelBuilderFactory extends Supplier<QianfanEmbeddingModel.QianfanEmbeddingModelBuilder> {
}
