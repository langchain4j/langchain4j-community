package dev.langchain4j.community.model.zhipu.spi;

import dev.langchain4j.community.model.zhipu.ZhipuAiAssistant;
import java.util.function.Supplier;

/**
 * A factory for building {@link ZhipuAiAssistant.ZhipuAiAssistantBuilder} instances.
 */
public interface ZhipuAssistantBuilderFactory extends Supplier<ZhipuAiAssistant.ZhipuAiAssistantBuilder> {}
