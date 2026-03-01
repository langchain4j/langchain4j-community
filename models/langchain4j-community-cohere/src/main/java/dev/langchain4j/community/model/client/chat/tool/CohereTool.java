package dev.langchain4j.community.model.client.chat.tool;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereTool.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereTool {

    private final CohereToolType type;
    private final CohereFunction function;

    public CohereTool(Builder builder) {
        this.type = builder.type;
        this.function = builder.function;
    }

    public CohereToolType getType() { return type; }

    public CohereFunction getFunction() { return function; }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private CohereToolType type;
        private CohereFunction function;

        public Builder type(CohereToolType type) {
            this.type = type;
            return this;
        }

        public Builder function(CohereFunction function) {
            this.function = function;
            return this;
        }

        public CohereTool build() { return new CohereTool(this); }
    }
}
