package dev.langchain4j.community.qianfan.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = QianfanProperties.PREFIX)
public class QianfanProperties {

    static final String PREFIX = "langchain4j.community.qianfan";

    @NestedConfigurationProperty
    QianfanChatModelProperties chatModel;

    @NestedConfigurationProperty
    QianfanChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    QianfanLanguageModelProperties languageModel;

    @NestedConfigurationProperty
    QianfanLanguageModelProperties streamingLanguageModel;

    @NestedConfigurationProperty
    QianfanEmbeddingModelProperties embeddingModel;

    public QianfanChatModelProperties getChatModel() {
        return chatModel;
    }

    public void setChatModel(QianfanChatModelProperties chatModel) {
        this.chatModel = chatModel;
    }

    public QianfanChatModelProperties getStreamingChatModel() {
        return streamingChatModel;
    }

    public void setStreamingChatModel(QianfanChatModelProperties streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    public QianfanLanguageModelProperties getLanguageModel() {
        return languageModel;
    }

    public void setLanguageModel(QianfanLanguageModelProperties languageModel) {
        this.languageModel = languageModel;
    }

    public QianfanLanguageModelProperties getStreamingLanguageModel() {
        return streamingLanguageModel;
    }

    public void setStreamingLanguageModel(QianfanLanguageModelProperties streamingLanguageModel) {
        this.streamingLanguageModel = streamingLanguageModel;
    }

    public QianfanEmbeddingModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(QianfanEmbeddingModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
