package dev.langchain4j.community.model.xinference.client.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.shared.CompletionUsage;
import java.util.List;

@JsonDeserialize(builder = EmbeddingResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EmbeddingResponse {
    private final String model;
    private final List<Embedding> data;
    private final CompletionUsage usage;

    private EmbeddingResponse(Builder builder) {
        model = builder.model;
        data = builder.data;
        usage = builder.usage;
    }

    public String getModel() {
        return model;
    }

    public List<Embedding> getData() {
        return data;
    }

    public CompletionUsage getUsage() {
        return usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private List<Embedding> data;
        private CompletionUsage usage;

        private Builder() {}

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder data(List<Embedding> val) {
            data = val;
            return this;
        }

        public Builder usage(CompletionUsage val) {
            usage = val;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this);
        }
    }
}
