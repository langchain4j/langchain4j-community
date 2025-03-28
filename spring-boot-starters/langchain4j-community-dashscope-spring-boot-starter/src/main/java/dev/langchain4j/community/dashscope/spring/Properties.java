package dev.langchain4j.community.dashscope.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = Properties.PREFIX)
public class Properties {

    static final String PREFIX = "langchain4j.community.dashscope";

    @NestedConfigurationProperty
    private ChatModelProperties chatModel;

    @NestedConfigurationProperty
    private ChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    private LanguageModelProperties languageModel;

    @NestedConfigurationProperty
    private LanguageModelProperties streamingLanguageModel;

    @NestedConfigurationProperty
    private EmbeddingModelProperties embeddingModel;

    @NestedConfigurationProperty
    private TokenizerProperties tokenizer;

    public ChatModelProperties getChatModel() {
        return chatModel;
    }

    public void setChatModel(ChatModelProperties chatModel) {
        this.chatModel = chatModel;
    }

    public ChatModelProperties getStreamingChatModel() {
        return streamingChatModel;
    }

    public void setStreamingChatModel(ChatModelProperties streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    public LanguageModelProperties getLanguageModel() {
        return languageModel;
    }

    public void setLanguageModel(LanguageModelProperties languageModel) {
        this.languageModel = languageModel;
    }

    public LanguageModelProperties getStreamingLanguageModel() {
        return streamingLanguageModel;
    }

    public void setStreamingLanguageModel(LanguageModelProperties streamingLanguageModel) {
        this.streamingLanguageModel = streamingLanguageModel;
    }

    public EmbeddingModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(EmbeddingModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public TokenizerProperties getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(TokenizerProperties tokenizer) {
        this.tokenizer = tokenizer;
    }
}
