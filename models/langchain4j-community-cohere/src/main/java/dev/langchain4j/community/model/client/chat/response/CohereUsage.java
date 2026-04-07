package dev.langchain4j.community.model.client.chat.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereUsage.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereUsage {

    private final CohereBilledUnits billedUnits;
    private final CohereTokens tokens;
    private final Integer cachedTokens;

    private CohereUsage(Builder builder) {
        this.billedUnits = builder.billedUnits;
        this.tokens = builder.tokens;
        this.cachedTokens = builder.cachedTokens;
    }

    public CohereBilledUnits getBilledUnits() { return billedUnits; }

    public CohereTokens getTokens() { return tokens; }

    public Integer getCachedTokens() { return cachedTokens; }

    @Override
    public String toString() {
        return "CohereUsage{"
                + "billedUnits=" + billedUnits
                + ", tokens=" + tokens
                + ", cachedTokens=" + cachedTokens
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(billedUnits, tokens, cachedTokens); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereUsage that && equalsTo(that);
    }

    private boolean equalsTo(CohereUsage that) {
        return Objects.equals(billedUnits, that.billedUnits)
                && Objects.equals(tokens, that.tokens)
                && Objects.equals(cachedTokens, that.cachedTokens);
    }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private CohereBilledUnits billedUnits;
        private CohereTokens tokens;
        private Integer cachedTokens;

        public Builder billedUnits(CohereBilledUnits billedUnits) {
            this.billedUnits = billedUnits;
            return this;
        }

        public Builder tokens(CohereTokens tokens) {
            this.tokens = tokens;
            return this;
        }

        public Builder cachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
            return this;
        }

        public CohereUsage build() { return new CohereUsage(this); }
    }
}
