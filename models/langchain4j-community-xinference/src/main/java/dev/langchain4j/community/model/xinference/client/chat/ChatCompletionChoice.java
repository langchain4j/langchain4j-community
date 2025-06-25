package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.message.AssistantMessage;

@JsonDeserialize(builder = ChatCompletionChoice.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ChatCompletionChoice {
    private final Integer index;
    private final String finishReason;
    private final AssistantMessage message;
    private final Delta delta;

    private ChatCompletionChoice(Builder builder) {
        index = builder.index;
        finishReason = builder.finishReason;
        message = builder.message;
        delta = builder.delta;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getIndex() {
        return index;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public AssistantMessage getMessage() {
        return message;
    }

    public Delta getDelta() {
        return delta;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Integer index;
        private String finishReason;
        private AssistantMessage message;
        private Delta delta;

        private Builder() {}

        public Builder index(Integer val) {
            index = val;
            return this;
        }

        public Builder finishReason(String val) {
            finishReason = val;
            return this;
        }

        public Builder message(AssistantMessage val) {
            message = val;
            return this;
        }

        public Builder delta(Delta val) {
            delta = val;
            return this;
        }

        public ChatCompletionChoice build() {
            return new ChatCompletionChoice(this);
        }
    }
}
