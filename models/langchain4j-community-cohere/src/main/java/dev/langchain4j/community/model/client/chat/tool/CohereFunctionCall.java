package dev.langchain4j.community.model.client.chat.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereFunctionCall.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereFunctionCall {

    private final String name;
    private final String arguments;

    private CohereFunctionCall(Builder builder) {
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    public String getName() { return name; }

    public String getArguments() {
        return arguments;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CohereFunctionCall from (String name, String arguments) {
        return builder().name(name).arguments(arguments).build();
    }

    @Override
    public String toString() {
        return "CohereFunctionCall{"
                + "name=" + name
                + ", arguments=" + arguments
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(name, arguments); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereFunctionCall that
                && Objects.equals(name, that.name)
                && Objects.equals(arguments, that.arguments);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String name;
        private String arguments;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public CohereFunctionCall build() {
            return new CohereFunctionCall(this);
        }
    }
}
