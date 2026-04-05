package dev.langchain4j.community.model.client.chat.response;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

@JsonDeserialize(builder = CohereLogprobs.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereLogprobs {

    private final List<Integer> tokenIds;
    private final String text;
    private final List<Double> logprobs;

    private CohereLogprobs(Builder builder) {
        this.tokenIds = builder.tokenIds;
        this.text = builder.text;
        this.logprobs = builder.logprobs;
    }

    public List<Integer> getTokenIds() { return copy(tokenIds); }

    public String getText() { return text; }

    public List<Double> getLogprobs() { return copy(logprobs); }

    public static Builder builder() { return new Builder(); }

    @Override
    public String toString() {
        return "CohereLogprobs{ "
                + "tokenIds=" + tokenIds
                + ", text=" + quoted(text)
                + ", logprobs=" + logprobs
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenIds, text, logprobs);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereLogprobs that && equalsTo(that);
    }

    private boolean equalsTo(CohereLogprobs that) {
        return Objects.equals(tokenIds, that.tokenIds)
                && Objects.equals(text, that.text)
                && Objects.equals(logprobs, that.logprobs);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private List<Integer> tokenIds;
        private String text;
        private List<Double> logprobs;

        public Builder tokenIds(List<Integer> tokenIds) {
            this.tokenIds = tokenIds;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder logprobs(List<Double> logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public CohereLogprobs build() { return new CohereLogprobs(this); }
    }
}
