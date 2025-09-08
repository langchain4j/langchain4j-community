package dev.langchain4j.community.model.dashscope;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * TODO
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenStreamingPartialToolCallIT {}
