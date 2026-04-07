package dev.langchain4j.community.model.client.chat.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereTokens.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereTokens {

    private final Integer inputTokens;
    private final Integer outputTokens;

    private CohereTokens(Builder builder) {
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
    }

    public Integer getInputTokens() { return inputTokens; }

    public Integer getOutputTokens() { return outputTokens; }

    @Override
    public String toString() {
        return "CohereTokens{"
                + "inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(inputTokens, outputTokens); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereTokens that
                && Objects.equals(inputTokens, that.inputTokens)
                && Objects.equals(outputTokens, that.outputTokens);
    }

    public static CohereTokens of(Integer inputTokens, Integer outputTokens) {
        return builder().inputTokens(inputTokens).outputTokens(outputTokens).build();
    }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private Integer inputTokens;
        private Integer outputTokens;

        public Builder inputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public CohereTokens build() { return new CohereTokens(this); }
    }
}
