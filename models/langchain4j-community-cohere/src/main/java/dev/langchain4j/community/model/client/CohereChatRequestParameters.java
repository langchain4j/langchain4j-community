package dev.langchain4j.community.model.client;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

public class CohereChatRequestParameters extends DefaultChatRequestParameters {

    public static final CohereChatRequestParameters EMPTY = builder().build();

    private final String thinkingType;
    private final Integer thinkingTokenBudget;
    private final Boolean stream;
    private final String safetyMode;
    private final Integer priority;
    private final Integer seed;
    private final Boolean logprobs;
    private final Boolean strictTools;

    private CohereChatRequestParameters(Builder builder) {
        super(builder);

        this.thinkingType = builder.thinkingType;
        this.thinkingTokenBudget = builder.thinkingTokenBudget;
        this.stream = builder.stream;
        this.safetyMode = builder.safetyMode;
        this.priority = builder.priority;
        this.seed = builder.seed;
        this.logprobs = builder.logprobs;
        this.strictTools = builder.strictTools;
    }

    public String thinkingType() { return thinkingType; }

    public Integer thinkingTokenBudget() { return thinkingTokenBudget; }

    public Boolean stream() { return stream; }

    public String safetyMode() { return safetyMode; }

    public Integer priority() { return priority; }

    public Integer seed() { return seed; }

    public Boolean logprobs() { return logprobs; }

    public Boolean strictTools() { return strictTools; }

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
        return "CohereChatRequestParameters{"
                + "modelName=" + quoted(modelName())
                + ", temperature=" + temperature()
                + ", topP=" + topP()
                + ", topK=" + topK()
                + ", frequencyPenalty=" + frequencyPenalty()
                + ", presencePenalty=" + presencePenalty()
                + ", maxOutputTokens=" + maxOutputTokens()
                + ", stopSequences=" + stopSequences()
                + ", toolSpecifications=" + toolSpecifications()
                + ", toolChoice=" + toolChoice()
                + ", responseFormat=" + responseFormat()
                + ", thinkingType=" + quoted(thinkingType())
                + ", thinkingTokenBudget=" + thinkingTokenBudget()
                + ", stream=" + stream()
                + ", safetyMode=" + quoted(safetyMode())
                + ", priority=" + priority()
                + ", seed=" + seed()
                + ", logprobs=" + logprobs()
                + ", strictTools=" + strictTools()
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                thinkingType,
                thinkingTokenBudget,
                stream,
                safetyMode,
                priority,
                seed,
                logprobs,
                strictTools);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereChatRequestParameters that && equalsTo(that);
    }

    private boolean equalsTo(CohereChatRequestParameters that) {
        return Objects.equals(thinkingType, that.thinkingType)
                && Objects.equals(thinkingTokenBudget, that.thinkingTokenBudget)
                && Objects.equals(stream, that.stream)
                && Objects.equals(safetyMode, that.safetyMode)
                && Objects.equals(priority, that.priority)
                && Objects.equals(seed, that.seed)
                && Objects.equals(logprobs, that.logprobs)
                && Objects.equals(strictTools, that.strictTools);
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String thinkingType;
        private Integer thinkingTokenBudget;
        private Boolean stream;
        private String safetyMode;
        private Integer priority;
        private Integer seed;
        private Boolean logprobs;
        private Boolean strictTools;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);

            if (parameters instanceof CohereChatRequestParameters cohereParameters) {
                this.thinkingType = getOrDefault(cohereParameters.thinkingType, thinkingType);
                this.thinkingTokenBudget = getOrDefault(cohereParameters.thinkingTokenBudget, thinkingTokenBudget);
                this.stream = getOrDefault(cohereParameters.stream, stream);
                this.safetyMode = getOrDefault(cohereParameters.safetyMode, safetyMode);
                this.priority = getOrDefault(cohereParameters.priority, priority);
                this.seed = getOrDefault(cohereParameters.seed, seed);
                this.logprobs = getOrDefault(cohereParameters.logprobs, logprobs);
                this.strictTools = getOrDefault(cohereParameters.strictTools, strictTools);
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

        public Builder safetyMode(String safetyMode) {
            this.safetyMode = safetyMode;
            return this;
        }

        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public CohereChatRequestParameters build() {
            return new CohereChatRequestParameters(this);
        }
    }
}
