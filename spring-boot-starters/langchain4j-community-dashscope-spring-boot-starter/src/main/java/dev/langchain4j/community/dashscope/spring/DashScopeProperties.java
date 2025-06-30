package dev.langchain4j.community.dashscope.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = DashScopeProperties.PREFIX)
public class DashScopeProperties {

    static final String PREFIX = "langchain4j.community.dashscope";

    @NestedConfigurationProperty
    private DashScopeChatModelProperties chatModel;

    @NestedConfigurationProperty
    private DashScopeChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    private DashScopeLanguageModelProperties languageModel;

    @NestedConfigurationProperty
    private DashScopeLanguageModelProperties streamingLanguageModel;

    @NestedConfigurationProperty
    private DashScopeEmbeddingModelProperties embeddingModel;

    @NestedConfigurationProperty
    private DashScopeTokenizerProperties tokenizer;

    public DashScopeChatModelProperties getChatModel() {
        return chatModel;
    }

    public void setChatModel(DashScopeChatModelProperties chatModel) {
        this.chatModel = chatModel;
    }

    public DashScopeChatModelProperties getStreamingChatModel() {
        return streamingChatModel;
    }

    public void setStreamingChatModel(DashScopeChatModelProperties streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    public DashScopeLanguageModelProperties getLanguageModel() {
        return languageModel;
    }

    public void setLanguageModel(DashScopeLanguageModelProperties languageModel) {
        this.languageModel = languageModel;
    }

    public DashScopeLanguageModelProperties getStreamingLanguageModel() {
        return streamingLanguageModel;
    }

    public void setStreamingLanguageModel(DashScopeLanguageModelProperties streamingLanguageModel) {
        this.streamingLanguageModel = streamingLanguageModel;
    }

    public DashScopeEmbeddingModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(DashScopeEmbeddingModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public DashScopeTokenizerProperties getTokenizer() {
        return tokenizer;
    }

    public void setTokenizer(DashScopeTokenizerProperties tokenizer) {
        this.tokenizer = tokenizer;
    }
}
