package dev.langchain4j.community.model.dashscope.spi;

import dev.langchain4j.community.model.dashscope.WanxImageModel;

import java.util.function.Supplier;

public interface WanxImageModelBuilderFactory extends Supplier<WanxImageModel.WanxImageModelBuilder> {
}
