package dev.langchain4j.community.zhipu.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = ZhipuAiProperties.PREFIX)
public class ZhipuAiProperties {

    static final String PREFIX = "langchain4j.community.zhipuai";

    @NestedConfigurationProperty
    private ZhipuAiChatModelProperties chatModel;

    @NestedConfigurationProperty
    private ZhipuAiStreamingChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    private ZhipuAiEmbeddingModelProperties embeddingModel;

    @NestedConfigurationProperty
    private ZhipuAiImageModelProperties imageModel;

    public ZhipuAiChatModelProperties getChatModel() {
        return chatModel;
    }

    public void setChatModel(ZhipuAiChatModelProperties chatModel) {
        this.chatModel = chatModel;
    }

    public ZhipuAiStreamingChatModelProperties getStreamingChatModel() {
        return streamingChatModel;
    }

    public void setStreamingChatModel(ZhipuAiStreamingChatModelProperties streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    public ZhipuAiEmbeddingModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(ZhipuAiEmbeddingModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public ZhipuAiImageModelProperties getImageModel() {
        return imageModel;
    }

    public void setImageModel(ZhipuAiImageModelProperties imageModel) {
        this.imageModel = imageModel;
    }
}
