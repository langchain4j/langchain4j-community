package dev.langchain4j.community.model.xinference.client.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = RerankDocument.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class RerankDocument {
    private final String text;

    private RerankDocument(Builder builder) {
        text = builder.text;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getText() {
        return text;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String text;

        private Builder() {}

        public Builder text(String val) {
            text = val;
            return this;
        }

        public RerankDocument build() {
            return new RerankDocument(this);
        }
    }
}
