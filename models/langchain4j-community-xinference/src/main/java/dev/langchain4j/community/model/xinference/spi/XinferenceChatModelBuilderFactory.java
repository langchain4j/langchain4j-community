package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceChatModel;

import java.util.function.Supplier;

public interface XinferenceChatModelBuilderFactory extends Supplier<XinferenceChatModel.XinferenceChatModelBuilder> {
}
