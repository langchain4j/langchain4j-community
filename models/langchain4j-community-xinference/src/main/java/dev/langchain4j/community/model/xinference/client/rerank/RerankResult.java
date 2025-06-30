package dev.langchain4j.community.model.xinference.client.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = RerankResult.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class RerankResult {
    private final Integer index;
    private final Double relevanceScore;
    private final RerankDocument document;

    private RerankResult(Builder builder) {
        index = builder.index;
        relevanceScore = builder.relevanceScore;
        document = builder.document;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getIndex() {
        return index;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public RerankDocument getDocument() {
        return document;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Integer index;
        private Double relevanceScore;
        private RerankDocument document;

        private Builder() {}

        public Builder index(Integer val) {
            index = val;
            return this;
        }

        public Builder relevanceScore(Double val) {
            relevanceScore = val;
            return this;
        }

        public Builder document(RerankDocument val) {
            document = val;
            return this;
        }

        public RerankResult build() {
            return new RerankResult(this);
        }
    }
}
