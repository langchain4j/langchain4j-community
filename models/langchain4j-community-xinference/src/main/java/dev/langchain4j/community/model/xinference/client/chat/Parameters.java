package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Map;

@JsonDeserialize(builder = Parameters.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Parameters {
    private final String type;
    private final String description;
    private final Map<String, Map<String, Object>> properties;
    private final List<String> required;

    private Parameters(Builder builder) {
        type = builder.type;
        description = builder.description;
        properties = builder.properties;
        required = builder.required;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Map<String, Object>> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String type;
        private String description;
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        private Builder() {}

        public Builder type(String val) {
            type = val;
            return this;
        }

        public Builder description(String val) {
            description = val;
            return this;
        }

        public Builder properties(Map<String, Map<String, Object>> val) {
            properties = val;
            return this;
        }

        public Builder required(List<String> val) {
            required = val;
            return this;
        }

        public Parameters build() {
            return new Parameters(this);
        }
    }
}
