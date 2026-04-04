package dev.langchain4j.community.model.client.chat.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereBilledUnits.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereBilledUnits {

    private final Double inputTokens;
    private final Double outputTokens;
    private final Double searchUnits;
    private final Double classifications;

    private CohereBilledUnits(Builder builder) {
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.searchUnits = builder.searchUnits;
        this.classifications = builder.classifications;
    }

    public Double getInputTokens() { return inputTokens; }

    public Double getOutputTokens() { return outputTokens; }

    public Double getSearchUnits() { return searchUnits; }

    public Double getClassifications() { return classifications; }

    @Override
    public String toString() {
        return "CohereBilledUnits{ "
                + "inputTokens = " + inputTokens
                + ", outputTokens = " + outputTokens
                + ", searchUnits = " + searchUnits
                + ", classifications = " + classifications
                + " }";
    }

    @Override
    public int hashCode() { return Objects.hash(inputTokens, outputTokens, searchUnits, classifications); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereBilledUnits that && equalsTo(that);
    }

    private boolean equalsTo(CohereBilledUnits that) {
        return Objects.equals(inputTokens, that.inputTokens)
                && Objects.equals(outputTokens, that.outputTokens)
                && Objects.equals(searchUnits, that.searchUnits)
                && Objects.equals(classifications, that.classifications);
    }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Double inputTokens;
        private Double outputTokens;
        private Double searchUnits;
        private Double classifications;

        public Builder inputTokens(Double inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(Double outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder searchUnits(Double searchUnits) {
            this.searchUnits = searchUnits;
            return this;
        }

        public Builder classifications(Double classifications) {
            this.classifications = classifications;
            return this;
        }

        public CohereBilledUnits build() { return new CohereBilledUnits(this); }
    }
}
