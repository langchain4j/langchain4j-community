package dev.langchain4j.community.model.client.tokenize;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = CohereTokenizeResponse.Builder.class)
public class CohereTokenizeResponse {

    private final List<Integer> tokens;
    private final List<String> tokenStrings;

    private CohereTokenizeResponse(Builder builder) {
        this.tokens = builder.tokens;
        this.tokenStrings = builder.tokenStrings;
    }

    public List<Integer> getTokens() {
        return tokens;
    }

    public List<String> getTokenStrings() {
        return tokenStrings;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "CohereTokenizeResponse{ " + "tokens=" + tokens + ", tokenStrings=" + tokenStrings + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokens, tokenStrings);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereTokenizeResponse that
                && Objects.equals(tokens, that.tokens)
                && Objects.equals(tokenStrings, that.tokenStrings);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private List<Integer> tokens;
        private List<String> tokenStrings;

        public Builder tokens(List<Integer> tokens) {
            this.tokens = tokens;
            return this;
        }

        public Builder tokenStrings(List<String> tokenStrings) {
            this.tokenStrings = tokenStrings;
            return this;
        }

        public CohereTokenizeResponse build() {
            return new CohereTokenizeResponse(this);
        }
    }
}
