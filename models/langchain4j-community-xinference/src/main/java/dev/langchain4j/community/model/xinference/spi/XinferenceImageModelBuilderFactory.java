package dev.langchain4j.community.model.xinference.spi;

import dev.langchain4j.community.model.xinference.XinferenceImageModel;

import java.util.function.Supplier;

public interface XinferenceImageModelBuilderFactory extends Supplier<XinferenceImageModel.XinferenceImageModelBuilder> {
}
