package dev.langchain4j.community.model.client.chat.tool;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.tool.CohereToolType.FUNCTION;

@JsonDeserialize(builder = CohereToolCall.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereToolCall {

    private final String id;
    private final CohereToolType type;
    private final CohereFunctionCall function;

    private CohereToolCall(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.function = builder.function;
    }

    public String getId() { return id; }

    public CohereToolType getType() { return type; }

    public CohereFunctionCall getFunction() { return function; }

    public static Builder builder() { return new Builder(); }

    public static CohereToolCall from(String id, CohereFunctionCall function) {
        return CohereToolCall.builder()
                .type(FUNCTION)
                .id(id)
                .function(function)
                .build();
    }

    @Override
    public String toString() {
        return "CohereToolCall{ "
                + "type = " + type
                + ", id = " + id
                + ", function = " + function
                + " }";
    }

    @Override
    public int hashCode() { return Objects.hash(type, id, function); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereToolCall that && equalsTo(that);
    }

    private boolean equalsTo(CohereToolCall that) {
        return Objects.equals(type, that.type)
                && Objects.equals(id, that.id)
                && Objects.equals(function, that.function);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String id;
        private CohereToolType type;
        private CohereFunctionCall function;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(CohereToolType type) {
            this.type = type;
            return this;
        }

        public Builder function(CohereFunctionCall function) {
            this.function = function;
            return this;
        }

        public CohereToolCall build() {
            return new CohereToolCall(this);
        }
    }
}
