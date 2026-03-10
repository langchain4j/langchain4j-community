package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Map;
import java.util.Objects;

public class QwenJsonSchema {
    private final String name;
    private final String description;
    private final Boolean strict;
    private final Map<String, Object> schema;

    public QwenJsonSchema(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.strict = builder.strict;
        this.schema = builder.schema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getStrict() {
        return strict;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QwenJsonSchema that)) return false;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(strict, that.strict)
                && Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, strict, schema);
    }

    @Override
    public String toString() {
        return "JsonSchema{"
                + "name=" + quoted(name)
                + ", description=" + quoted(description)
                + ", strict=" + strict
                + ", schema=" + schema
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private Boolean strict;
        private Map<String, Object> schema;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema;
            return this;
        }

        public QwenJsonSchema build() {
            return new QwenJsonSchema(this);
        }
    }
}
