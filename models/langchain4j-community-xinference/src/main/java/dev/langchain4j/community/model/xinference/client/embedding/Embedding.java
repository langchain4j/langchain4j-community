package dev.langchain4j.community.model.xinference.client.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;

@JsonDeserialize(builder = Embedding.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Embedding {
    private final Integer index;
    private final List<Float> embedding;

    private Embedding(Builder builder) {
        index = builder.index;
        embedding = builder.embedding;
    }

    public Integer getIndex() {
        return index;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Integer index;
        private List<Float> embedding;

        private Builder() {}

        public Builder index(Integer val) {
            index = val;
            return this;
        }

        public Builder embedding(List<Float> val) {
            embedding = val;
            return this;
        }

        public Embedding build() {
            return new Embedding(this);
        }
    }
}
