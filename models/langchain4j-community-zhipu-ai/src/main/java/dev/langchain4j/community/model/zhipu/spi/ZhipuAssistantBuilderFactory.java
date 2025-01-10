package dev.langchain4j.community.model.zhipu.spi;

import dev.langchain4j.community.model.zhipu.ZhipuAiAssistant;
import java.util.function.Supplier;

/**
 * A factory for building {@link ZhipuAssistantChatModel.ZhipuAssistantChatModelBuilder} instances.
 */
public interface ZhipuAssistantBuilderFactory extends Supplier<ZhipuAiAssistant.ZhipuAssistantChatModelBuilder> {}
