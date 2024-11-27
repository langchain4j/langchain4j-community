package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceStreamingChatModel;

import java.util.function.Supplier;

public interface XinferenceStreamingChatModelBuilderFactory extends Supplier<XinferenceStreamingChatModel.XinferenceStreamingChatModelBuilder> {
}
