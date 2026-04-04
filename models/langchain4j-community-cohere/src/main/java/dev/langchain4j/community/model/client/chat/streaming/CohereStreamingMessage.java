package dev.langchain4j.community.model.client.chat.streaming;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereStreamingMessage.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereStreamingMessage {

    private final CohereStreamingContent content;
    private final CohereToolCall toolCalls;

    private CohereStreamingMessage(Builder builder) {
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
    }

    public CohereStreamingContent getContent() { return content; }

    public CohereToolCall getToolCalls() { return toolCalls; }

    public String toString() {
        return "CohereStreamingMessage{"
                + "content=" + content
                + ", toolCalls=" + toolCalls
            + "}";
    }

    public int hashCode() { return Objects.hash(content, toolCalls); }

    public boolean equals(Object o) {
        return o instanceof CohereStreamingMessage message
                && Objects.equals(content, message.content)
                && Objects.equals(toolCalls, message.toolCalls);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private CohereStreamingContent content;
        private CohereToolCall toolCalls;

        public Builder content(CohereStreamingContent content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(CohereToolCall toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public CohereStreamingMessage build() {
            return new CohereStreamingMessage(this);
        }
    }
}
