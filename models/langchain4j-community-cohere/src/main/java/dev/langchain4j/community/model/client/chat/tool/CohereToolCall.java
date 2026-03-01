package dev.langchain4j.community.model.client.chat.tool;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereToolCall.Builder.class)
@JsonInclude(NON_NULL)
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

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
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
