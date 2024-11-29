package dev.langchain4j.community.model.dashscope.spi;

import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenStreamingChatModel.QwenStreamingChatModelBuilder} instances.
 */
public interface QwenStreamingChatModelBuilderFactory extends Supplier<QwenStreamingChatModel.QwenStreamingChatModelBuilder> {
}
