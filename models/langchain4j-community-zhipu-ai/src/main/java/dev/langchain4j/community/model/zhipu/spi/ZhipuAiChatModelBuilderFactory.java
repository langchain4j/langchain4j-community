package dev.langchain4j.community.model.zhipu.spi;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link ZhipuAiChatModel.ZhipuAiChatModelBuilder} instances.
 */
public interface ZhipuAiChatModelBuilderFactory extends Supplier<ZhipuAiChatModel.ZhipuAiChatModelBuilder> {
}
