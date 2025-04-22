package dev.langchain4j.community.model.dashscope.spi;

import dev.langchain4j.community.model.dashscope.QwenTokenCountEstimator;
import java.util.function.Supplier;

public interface QwenTokenizerBuilderFactory extends Supplier<QwenTokenCountEstimator.QwenTokenizerBuilder> {}
