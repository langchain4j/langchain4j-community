package dev.langchain4j.community.model.xinference.client.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;

@JsonDeserialize(builder = RerankResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class RerankResponse {
    private final String id;
    private final List<RerankResult> results;
    private final RerankMeta meta;

    private RerankResponse(Builder builder) {
        id = builder.id;
        results = builder.results;
        meta = builder.meta;
    }

    public String getId() {
        return id;
    }

    public List<RerankResult> getResults() {
        return results;
    }

    public RerankMeta getMeta() {
        return meta;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String id;
        private List<RerankResult> results;
        private RerankMeta meta;

        private Builder() {}

        public Builder id(String val) {
            id = val;
            return this;
        }

        public Builder results(List<RerankResult> val) {
            results = val;
            return this;
        }

        public Builder meta(RerankMeta val) {
            meta = val;
            return this;
        }

        public RerankResponse build() {
            return new RerankResponse(this);
        }
    }
}
