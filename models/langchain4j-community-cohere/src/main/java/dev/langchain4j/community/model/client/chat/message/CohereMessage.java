package dev.langchain4j.community.model.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.message.content.CohereMessageContent;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;

import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.copy;

@JsonDeserialize(builder = CohereMessage.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereMessage {

    private final CohereRole role;
    private final List<CohereMessageContent> content;
    private final List<CohereToolCall> toolCalls;
    private final String toolCallId;

    private CohereMessage(Builder builder) {
        this.role = builder.role;
        this.content = copy(builder.content);
        this.toolCalls = copy(builder.toolCalls);
        this.toolCallId = builder.toolCallId;
    }

    // TODO: Define hashing better.
    public int hashCode() {
        return Objects.hash(role, content);
    }

    public CohereRole getRole() { return role; }

    public List<CohereMessageContent> getContent() { return content; }

    public List<CohereToolCall> getToolCalls() { return toolCalls; }

    public String getToolCallId() { return toolCallId; }

    public static Builder builder() { return new Builder(); }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof CohereMessage)) return false;
        CohereMessage that = (CohereMessage) o;
        // TODO: This comparison can cause an NPE.
        return role == that.role && content.equals(that.content);
    }

    public String toString() {
        return "CohereMessage{"
                + "role=" + role
                + ", content=" + content
                + ", toolCallId=" + toolCallId
                + "}";
    }

    public static class Builder {

        private CohereRole role;
        private List<CohereMessageContent> content;
        private List<CohereToolCall> toolCalls;
        private String toolCallId;

        public Builder role(CohereRole role) {
            this.role = role;
            return this;
        }

        public Builder content(List<CohereMessageContent> content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(List<CohereToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public CohereMessage build() {
            return new CohereMessage(this);
        }
    }
}
