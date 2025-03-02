package dev.langchain4j.community.dashscope.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = Properties.PREFIX)
public class Properties {

    static final String PREFIX = "langchain4j.community.dashscope";

    @NestedConfigurationProperty
    ChatModelProperties chatModel;

    @NestedConfigurationProperty
    ChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    LanguageModelProperties languageModel;

    @NestedConfigurationProperty
    LanguageModelProperties streamingLanguageModel;

    @NestedConfigurationProperty
    EmbeddingModelProperties embeddingModel;

    @NestedConfigurationProperty
    TokenizerProperties tokenizer;

    ChatModelProperties getChatModel() {
        return chatModel;
    }

    void setChatModel(ChatModelProperties chatModel) {
        this.chatModel = chatModel;
    }

    ChatModelProperties getStreamingChatModel() {
        return streamingChatModel;
    }

    void setStreamingChatModel(ChatModelProperties streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    LanguageModelProperties getLanguageModel() {
        return languageModel;
    }

    void setLanguageModel(LanguageModelProperties languageModel) {
        this.languageModel = languageModel;
    }

    LanguageModelProperties getStreamingLanguageModel() {
        return streamingLanguageModel;
    }

    void setStreamingLanguageModel(LanguageModelProperties streamingLanguageModel) {
        this.streamingLanguageModel = streamingLanguageModel;
    }

    EmbeddingModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    void setEmbeddingModel(EmbeddingModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    TokenizerProperties getTokenizer() {
        return tokenizer;
    }

    void setTokenizer(TokenizerProperties tokenizer) {
        this.tokenizer = tokenizer;
    }
}
