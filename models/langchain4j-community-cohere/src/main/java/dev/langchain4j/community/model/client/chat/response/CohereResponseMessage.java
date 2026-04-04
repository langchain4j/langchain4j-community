package dev.langchain4j.community.model.client.chat.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.client.chat.message.CohereRole;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.copy;

@JsonDeserialize(builder = CohereResponseMessage.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereResponseMessage {

    private final CohereRole role;
    private final List<CohereToolCall> toolCalls;
    private final List<CohereContent> content;

    private CohereResponseMessage(Builder builder) {
        this.role = builder.role;
        this.toolCalls = builder.toolCalls;
        this.content = builder.content;
    }

    public CohereRole getRole() { return role; }

    public List<CohereToolCall> getToolCalls() { return copy(toolCalls); }

    public List<CohereContent> getContent() { return content; }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private CohereRole role;
        private List<CohereToolCall> toolCalls;
        private List<CohereContent> content;

        public Builder role(CohereRole role) {
            this.role = role;
            return this;
        }

        public Builder toolCalls(List<CohereToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder content(List<CohereContent> content) {
            this.content = content;
            return this;
        }

        public CohereResponseMessage build() {
            return new CohereResponseMessage(this);
        }
    }
}
