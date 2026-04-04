package dev.langchain4j.community.model.util;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

public class CohereChatRequestParameters extends DefaultChatRequestParameters {

    private final String thinkingType;
    private final Integer thinkingTokenBudget;
    private final Boolean stream;

    private CohereChatRequestParameters(Builder builder) {
        super(builder);

        this.thinkingType = builder.thinkingType;
        this.thinkingTokenBudget = builder.thinkingTokenBudget;
        this.stream = builder.stream;
    }

    public String thinkingType() { return thinkingType; }

    public Integer thinkingTokenBudget() { return thinkingTokenBudget; }

    public Boolean stream() { return stream; }

    @Override
    public CohereChatRequestParameters overrideWith(ChatRequestParameters that) {
        return CohereChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public CohereChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return CohereChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public String toString() {
        return "CohereChatRequestParameters{" + "modelName="
                + quoted(modelName()) + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", thinkingType="
                + quoted(thinkingType()) + ", thinkingTokenBudget="
                + thinkingTokenBudget() + ", stream="
                + stream() + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thinkingType, thinkingTokenBudget, stream);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CohereChatRequestParameters)) return false;
        if (!super.equals(o)) return false;
        CohereChatRequestParameters that = (CohereChatRequestParameters) o;
        return Objects.equals(thinkingType, that.thinkingType)
                && Objects.equals(thinkingTokenBudget, that.thinkingTokenBudget)
                && Objects.equals(stream, that.stream);
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String thinkingType;
        private Integer thinkingTokenBudget;
        private Boolean stream;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);

            if (parameters instanceof CohereChatRequestParameters cohereParameters) {
                this.thinkingType = getOrDefault(cohereParameters.thinkingType, thinkingType);
                this.thinkingTokenBudget = getOrDefault(cohereParameters.thinkingTokenBudget, thinkingTokenBudget);
                this.stream = getOrDefault(cohereParameters.stream, stream);
            }

            return this;
        }

        public Builder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        public Builder thinkingTokenBudget(Integer thinkingTokenBudget) {
            this.thinkingTokenBudget = thinkingTokenBudget;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public CohereChatRequestParameters build() {
            return new CohereChatRequestParameters(this);
        }
    }
}
