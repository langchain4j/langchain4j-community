package dev.langchain4j.community.model.zhipu.chat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Collections;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Delta {
    private String content;
    private String reasoningContent;
    private List<ToolCall> toolCalls;

    public Delta() {}

    private Delta(Builder builder) {
        this.content = builder.content;
        this.reasoningContent = builder.reasoningContent;
        this.toolCalls = builder.toolCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public static final class Builder {
        private String content;
        private String reasoningContent;
        private List<ToolCall> toolCalls;

        private Builder() {}

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            if (toolCalls != null) {
                this.toolCalls = Collections.unmodifiableList(toolCalls);
            }

            return this;
        }

        public Delta build() {
            return new Delta(this);
        }
    }
}
