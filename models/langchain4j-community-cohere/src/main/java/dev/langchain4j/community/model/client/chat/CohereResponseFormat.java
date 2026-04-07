package dev.langchain4j.community.model.client.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.CohereResponseFormatType;

import java.util.Map;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereResponseFormat {

    private final CohereResponseFormatType type;
    private final Map<String, Object> jsonSchema;

    private CohereResponseFormat(Builder builder) {
        this.type = builder.type;
        this.jsonSchema = builder.jsonSchema;
    }

    public CohereResponseFormatType getType() { return type; }

    public Map<String, Object> getJsonSchema() { return jsonSchema; }

    public static Builder builder() { return new Builder(); }

    @Override
    public String toString() {
        return "CohereResponseFormat["
                + "type=" + type
                + ", jsonSchema=" + jsonSchema
                +"]";
    }

    @Override
    public int hashCode() { return Objects.hash(type, jsonSchema); }

    @Override
    public boolean equals(Object other) {
        return other instanceof CohereResponseFormat responseFormat && equalsTo(responseFormat);
    }

    private boolean equalsTo(CohereResponseFormat other) {
        return Objects.equals(type, other.type) && Objects.equals(jsonSchema, other.jsonSchema);
    }

    public static class Builder {

        private CohereResponseFormatType type;
        private Map<String, Object> jsonSchema;

        public Builder type(CohereResponseFormatType type) {
            this.type = type;
            return this;
        }

        public Builder jsonSchema(Map<String, Object> jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        public CohereResponseFormat build() { return new CohereResponseFormat(this); }
    }
}
