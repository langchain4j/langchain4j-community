package dev.langchain4j.community.model.xinference.client.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;

@JsonDeserialize(builder = EmbeddingRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EmbeddingRequest {
    private final String model;
    private final List<String> input;
    private final String user;

    private EmbeddingRequest(Builder builder) {
        model = builder.model;
        input = builder.input;
        user = builder.user;
    }

    public String getModel() {
        return model;
    }

    public List<String> getInput() {
        return input;
    }

    public String getUser() {
        return user;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private List<String> input;
        private String user;

        private Builder() {}

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder input(List<String> val) {
            input = val;
            return this;
        }

        public Builder user(String val) {
            user = val;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
