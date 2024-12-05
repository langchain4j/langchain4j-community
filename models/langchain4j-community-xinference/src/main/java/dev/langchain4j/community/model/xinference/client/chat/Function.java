package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Function.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Function {
    private final String name;
    private final String description;
    private final Parameters parameters;

    private Function(Builder builder) {
        name = builder.name;
        description = builder.description;
        parameters = builder.parameters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String name;
        private String description;
        private Parameters parameters;

        private Builder() {}

        public Builder name(String val) {
            name = val;
            return this;
        }

        public Builder description(String val) {
            description = val;
            return this;
        }

        public Builder parameters(Parameters val) {
            parameters = val;
            return this;
        }

        public Function build() {
            return new Function(this);
        }
    }
}
