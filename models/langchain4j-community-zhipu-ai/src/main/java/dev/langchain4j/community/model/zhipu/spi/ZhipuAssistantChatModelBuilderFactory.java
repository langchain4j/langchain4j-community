package dev.langchain4j.community.model.zhipu.spi;

import dev.langchain4j.community.model.zhipu.ZhipuAssistantChatModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link ZhipuAssistantChatModel.ZhipuAssistantChatModelBuilder} instances.
 */
public interface ZhipuAssistantBuilderFactory
        extends Supplier<ZhipuAssistantChatModel.ZhipuAssistantChatModelBuilder> {}
