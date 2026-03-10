package dev.langchain4j.community.model.dashscope;

import com.alibaba.dashscope.common.ResponseFormat;
import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class QwenJsonSchemaResponseFormat extends ResponseFormat {
    @SerializedName("json_schema")
    private final QwenJsonSchema jsonSchema;

    protected QwenJsonSchemaResponseFormat(final QwenJsonSchemaResponseFormatBuilder b) {
        super(b);
        this.jsonSchema = b.jsonSchema;
    }

    public QwenJsonSchema getJsonSchema() {
        return jsonSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QwenJsonSchemaResponseFormat that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), jsonSchema);
    }

    @Override
    public String toString() {
        return "JsonSchemaResponseFormat{" + "jsonSchema=" + jsonSchema + "} " + super.toString();
    }

    public static QwenJsonSchemaResponseFormatBuilder builder() {
        return new QwenJsonSchemaResponseFormatBuilder();
    }

    public static class QwenJsonSchemaResponseFormatBuilder
            extends ResponseFormat.ResponseFormatBuilder<
                    QwenJsonSchemaResponseFormat, QwenJsonSchemaResponseFormatBuilder> {
        private QwenJsonSchema jsonSchema;

        private QwenJsonSchemaResponseFormatBuilder() {
            super.type("json_schema");
        }

        @Override
        protected QwenJsonSchemaResponseFormatBuilder self() {
            return this;
        }

        public QwenJsonSchemaResponseFormatBuilder jsonSchema(QwenJsonSchema jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        @Override
        public QwenJsonSchemaResponseFormat build() {
            return new QwenJsonSchemaResponseFormat(this);
        }
    }
}
