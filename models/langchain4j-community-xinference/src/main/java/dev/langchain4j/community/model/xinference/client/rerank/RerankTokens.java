package dev.langchain4j.community.model.xinference.client.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = RerankTokens.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class RerankTokens {
    private final Integer inputTokens;
    private final Integer outputTokens;

    private RerankTokens(Builder builder) {
        inputTokens = builder.inputTokens;
        outputTokens = builder.outputTokens;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Integer inputTokens;
        private Integer outputTokens;

        private Builder() {}

        public Builder inputTokens(Integer val) {
            inputTokens = val;
            return this;
        }

        public Builder outputTokens(Integer val) {
            outputTokens = val;
            return this;
        }

        public RerankTokens build() {
            return new RerankTokens(this);
        }
    }
}
