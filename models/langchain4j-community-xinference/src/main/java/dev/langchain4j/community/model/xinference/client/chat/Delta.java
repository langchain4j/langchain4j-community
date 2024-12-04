package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.message.ToolCall;
import java.util.List;

@JsonDeserialize(builder = Delta.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Delta {
    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;

    private Delta(Builder builder) {
        role = builder.role;
        content = builder.content;
        toolCalls = builder.toolCalls;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Role role;
        private String content;
        private List<ToolCall> toolCalls;

        private Builder() {}

        public Builder role(Role val) {
            role = val;
            return this;
        }

        public Builder content(String val) {
            content = val;
            return this;
        }

        public Builder toolCalls(List<ToolCall> val) {
            toolCalls = val;
            return this;
        }

        public Delta build() {
            return new Delta(this);
        }
    }
}
