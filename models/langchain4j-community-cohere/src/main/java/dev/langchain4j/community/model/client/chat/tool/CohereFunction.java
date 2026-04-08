package dev.langchain4j.community.model.client.chat.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereFunction {

    private final String name;
    private final Map<String, Object> parameters;
    private final String description;

    private CohereFunction(Builder builder) {
        this.name = builder.name;
        this.parameters = builder.parameters;
        this.description = builder.description;
    }

    public String getName() { return name; }

    public Map<String, Object> getParameters() { return parameters; }

    public String getDescription() { return description; }

    public static Builder builder() { return new Builder(); }

    @Override
    public String toString() {
        return "CohereFunction{"
                + "name=" + quoted(name)
                + ", parameters=" + parameters
                + ", description=" + quoted(description)
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(name, parameters, description); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereFunction that && equalsTo(that);
    }

    private boolean equalsTo(CohereFunction that) {
        return Objects.equals(name, that.name)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(description, that.description);
    }

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
