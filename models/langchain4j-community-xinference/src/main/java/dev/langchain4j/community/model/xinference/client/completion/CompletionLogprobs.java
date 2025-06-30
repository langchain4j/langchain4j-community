package dev.langchain4j.community.model.xinference.client.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Map;

@JsonDeserialize(builder = CompletionLogprobs.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionLogprobs {
    private final List<String> tokens;
    private final List<Double> tokenLogprobs;
    private final List<Map<String, Double>> topLogprobs;
    private final List<Integer> textOffset;

    private CompletionLogprobs(Builder builder) {
        tokens = builder.tokens;
        tokenLogprobs = builder.tokenLogprobs;
        topLogprobs = builder.topLogprobs;
        textOffset = builder.textOffset;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getTokens() {
        return tokens;
    }

    public List<Double> getTokenLogprobs() {
        return tokenLogprobs;
    }

    public List<Map<String, Double>> getTopLogprobs() {
        return topLogprobs;
    }

    public List<Integer> getTextOffset() {
        return textOffset;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private List<String> tokens;
        private List<Double> tokenLogprobs;
        private List<Map<String, Double>> topLogprobs;
        private List<Integer> textOffset;

        private Builder() {}

        public Builder tokens(List<String> val) {
            tokens = val;
            return this;
        }

        public Builder tokenLogprobs(List<Double> val) {
            tokenLogprobs = val;
            return this;
        }

        public Builder topLogprobs(List<Map<String, Double>> val) {
            topLogprobs = val;
            return this;
        }

        public Builder textOffset(List<Integer> val) {
            textOffset = val;
            return this;
        }

        public CompletionLogprobs build() {
            return new CompletionLogprobs(this);
        }
    }
}
