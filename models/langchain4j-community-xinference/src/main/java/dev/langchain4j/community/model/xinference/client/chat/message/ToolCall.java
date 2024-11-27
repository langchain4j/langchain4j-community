package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.ToolType;

@JsonDeserialize(builder = ToolCall.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ToolCall {
    private final String id;
    private final ToolType type;
    private final FunctionCall function;

    private ToolCall(Builder builder) {
        id = builder.id;
        type = builder.type;
        function = builder.function;
    }

    public String getId() {
        return id;
    }

    public ToolType getType() {
        return type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String id;
        private ToolType type;
        private FunctionCall function;

        private Builder() {
        }

        public Builder id(String val) {
            id = val;
            return this;
        }

        public Builder type(ToolType val) {
            type = val;
            return this;
        }

        public Builder function(FunctionCall val) {
            function = val;
            return this;
        }

        public ToolCall build() {
            return new ToolCall(this);
        }
    }
}
