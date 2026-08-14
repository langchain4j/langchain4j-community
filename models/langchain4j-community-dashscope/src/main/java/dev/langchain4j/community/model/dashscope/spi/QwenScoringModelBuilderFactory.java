package dev.langchain4j.community.model.dashscope.spi;

import dev.langchain4j.community.model.dashscope.QwenScoringModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link QwenScoringModel.QwenScoringModelBuilder} instances.
 */
public interface QwenScoringModelBuilderFactory extends Supplier<QwenScoringModel.QwenScoringModelBuilder> {}
