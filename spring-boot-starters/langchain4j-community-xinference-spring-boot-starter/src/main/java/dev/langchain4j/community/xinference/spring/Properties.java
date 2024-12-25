package dev.langchain4j.community.xinference.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = Properties.PREFIX)
public class Properties {
    static final String PREFIX = "langchain4j.community.xinference";

    @NestedConfigurationProperty
    private ChatModelProperties chatModel;

    @NestedConfigurationProperty
    private ChatModelProperties streamingChatModel;

    @NestedConfigurationProperty
    private EmbeddingModelProperties embeddingModel;

    @NestedConfigurationProperty
    private ImageModelProperties imageModel;

    @NestedConfigurationProperty
    private LanguageModelProperties languageModel;

    @NestedConfigurationProperty
    private LanguageModelProperties streamingLanguageModel;

    @NestedConfigurationProperty
    private ScoringModelProperties scoringModel;

    public ChatModelProperties getChatModel() {
        return chatModel;
    }

    public void setChatModel(final ChatModelProperties chatModel) {
        this.chatModel = chatModel;
    }

    public ChatModelProperties getStreamingChatModel() {
        return streamingChatModel;
    }

    public void setStreamingChatModel(final ChatModelProperties streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    public EmbeddingModelProperties getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(final EmbeddingModelProperties embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public ImageModelProperties getImageModel() {
        return imageModel;
    }

    public void setImageModel(final ImageModelProperties imageModel) {
        this.imageModel = imageModel;
    }

    public LanguageModelProperties getLanguageModel() {
        return languageModel;
    }

    public void setLanguageModel(final LanguageModelProperties languageModel) {
        this.languageModel = languageModel;
    }

    public LanguageModelProperties getStreamingLanguageModel() {
        return streamingLanguageModel;
    }

    public void setStreamingLanguageModel(final LanguageModelProperties streamingLanguageModel) {
        this.streamingLanguageModel = streamingLanguageModel;
    }

    public ScoringModelProperties getScoringModel() {
        return scoringModel;
    }

    public void setScoringModel(final ScoringModelProperties scoringModel) {
        this.scoringModel = scoringModel;
    }
}
