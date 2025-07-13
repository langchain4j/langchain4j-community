package dev.langchain4j.community.model.xinference.client.chat.message;

import static dev.langchain4j.internal.Utils.getOrDefault;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.Role;
import java.util.List;

@JsonDeserialize(builder = AssistantMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class AssistantMessage implements Message {

    private final Role role = Role.ASSISTANT;
    private final String content;
    private final String reasoningContent;
    private final List<ToolCall> toolCalls;

    private AssistantMessage(Builder builder) {
        // content should not be null
        content = getOrDefault(builder.content, "");
        reasoningContent = builder.reasoningContent;
        toolCalls = builder.toolCalls;
    }

    public static AssistantMessage of(String content, ToolCall... toolCalls) {
        return builder().content(content).toolCalls(List.of(toolCalls)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String content;
        private String reasoningContent;
        private List<ToolCall> toolCalls;

        private Builder() {}

        public Builder content(String val) {
            content = val;
            return this;
        }

        public Builder reasoningContent(String val) {
            reasoningContent = val;
            return this;
        }

        public Builder toolCalls(List<ToolCall> val) {
            toolCalls = val;
            return this;
        }

        public AssistantMessage build() {
            return new AssistantMessage(this);
        }
    }
}
