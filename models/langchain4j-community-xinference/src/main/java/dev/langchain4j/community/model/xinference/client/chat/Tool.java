package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Tool.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Tool {
    private final ToolType type = ToolType.FUNCTION;
    private final Function function;

    private Tool(Builder builder) {
        function = builder.function;
    }

    public ToolType getType() {
        return type;
    }

    public Function getFunction() {
        return function;
    }

    public static Tool of(Function function) {
        return builder().function(function).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Function function;

        private Builder() {}

        public Builder function(Function val) {
            function = val;
            return this;
        }

        public Tool build() {
            return new Tool(this);
        }
    }
}
