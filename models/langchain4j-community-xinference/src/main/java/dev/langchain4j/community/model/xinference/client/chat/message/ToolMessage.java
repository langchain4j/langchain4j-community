package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.Role;

@JsonDeserialize(builder = ToolMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ToolMessage implements Message {
    private final Role role = Role.TOOL;
    private final String content;
    private final String toolCallId;

    private ToolMessage(Builder builder) {
        content = builder.content;
        toolCallId = builder.toolCallId;
    }

    @Override
    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public static ToolMessage of(String toolCallId, String content) {
        return builder().content(content).toolCallId(toolCallId).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String content;
        private String toolCallId;

        private Builder() {}

        public Builder content(String val) {
            content = val;
            return this;
        }

        public Builder toolCallId(String val) {
            toolCallId = val;
            return this;
        }

        public ToolMessage build() {
            return new ToolMessage(this);
        }
    }
}
