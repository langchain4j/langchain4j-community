package dev.langchain4j.community.model.xinference.client.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;

@JsonDeserialize(builder = RerankMeta.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class RerankMeta {
    private final RerankTokens tokens;
    private final List<String> warnings;

    private RerankMeta(Builder builder) {
        tokens = builder.tokens;
        warnings = builder.warnings;
    }

    public static Builder builder() {
        return new Builder();
    }

    public RerankTokens getTokens() {
        return tokens;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private RerankTokens tokens;
        private List<String> warnings;

        private Builder() {}

        public Builder tokens(RerankTokens val) {
            tokens = val;
            return this;
        }

        public Builder warnings(List<String> val) {
            warnings = val;
            return this;
        }

        public RerankMeta build() {
            return new RerankMeta(this);
        }
    }
}
