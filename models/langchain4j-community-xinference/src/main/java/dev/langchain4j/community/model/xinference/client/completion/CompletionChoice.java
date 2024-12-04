package dev.langchain4j.community.model.xinference.client.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = CompletionChoice.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionChoice {
    private final String text;
    private final Integer index;
    private final CompletionLogprobs logprobs;
    private final String finishReason;

    private CompletionChoice(Builder builder) {
        text = builder.text;
        index = builder.index;
        logprobs = builder.logprobs;
        finishReason = builder.finishReason;
    }

    public String getText() {
        return text;
    }

    public Integer getIndex() {
        return index;
    }

    public CompletionLogprobs getLogprobs() {
        return logprobs;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String text;
        private Integer index;
        private CompletionLogprobs logprobs;
        private String finishReason;

        private Builder() {}

        public Builder text(String val) {
            text = val;
            return this;
        }

        public Builder index(Integer val) {
            index = val;
            return this;
        }

        public Builder logprobs(CompletionLogprobs val) {
            logprobs = val;
            return this;
        }

        public Builder finishReason(String val) {
            finishReason = val;
            return this;
        }

        public CompletionChoice build() {
            return new CompletionChoice(this);
        }
    }
}
