package dev.langchain4j.community.model.qianfan.spi;


import dev.langchain4j.community.model.qianfan.QianfanChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanChatModel.QianfanChatModelBuilder} instances.
 */
public interface QianfanChatModelBuilderFactory extends Supplier<QianfanChatModel.QianfanChatModelBuilder> {
}
