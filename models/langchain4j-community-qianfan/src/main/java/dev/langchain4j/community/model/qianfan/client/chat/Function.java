package dev.langchain4j.community.model.qianfan.client.chat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class Function {

    private final String name;
    private final String description;
    private final Parameters parameters;
    private final Responses responses;
    private final Examples examples;

    private Function(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
        this.examples = builder.examples;
        this.responses = builder.responses;
    }

    public static Builder builder() {
        return new Builder();
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

    public Responses getResponses() {
        return responses;
    }

    public Examples getExamples() {
        return examples;
    }

    @Override
    public String toString() {
        return "Function{" + "name='"
                + name + '\'' + ", description='"
                + description + '\'' + ", parameters="
                + parameters + ", responses="
                + responses + ", examples="
                + examples + '}';
    }

    public static final class Builder {

        private String name;
        private String description;
        private Parameters parameters;
        private Responses responses;
        private Examples examples;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(Parameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder responses(Responses responses) {
            this.responses = responses;
            return this;
        }

        public Builder examples(Examples examples) {
            this.examples = examples;
            return this;
        }

        public Function build() {
            return new Function(this);
        }
    }
}
