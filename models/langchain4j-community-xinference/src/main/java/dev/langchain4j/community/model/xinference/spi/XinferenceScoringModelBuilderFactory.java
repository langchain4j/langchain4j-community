package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceScoringModel;

import java.util.function.Supplier;

public interface XinferenceScoringModelBuilderFactory extends Supplier<XinferenceScoringModel.XinferenceScoringModelBuilder> {
}
