package dev.langchain4j.community.model.client.chat.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.copy;

@JsonDeserialize(builder = CohereFunction.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereFunction {

    private final String name;
    private final Map<String, Object> parameters;
    private final String description;

    private CohereFunction(Builder builder) {
        this.name = builder.name;
        this.parameters = copy(builder.parameters);
        this.description = builder.description;
    }

    public String getName() { return name; }

    public Map<String, Object> getParameters() { return parameters; }

    public String getDescription() { return description; }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String name;
        private Map<String, Object> parameters;
        private String description;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public CohereFunction build() {
            return new CohereFunction(this);
        }
    }
}
