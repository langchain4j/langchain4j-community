package dev.langchain4j.community.model.qianfan.spi;


import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QianfanStreamingChatModel.QianfanStreamingChatModelBuilder} instances.
 */
public interface QianfanStreamingChatModelBuilderFactory extends Supplier<QianfanStreamingChatModel.QianfanStreamingChatModelBuilder> {
}
