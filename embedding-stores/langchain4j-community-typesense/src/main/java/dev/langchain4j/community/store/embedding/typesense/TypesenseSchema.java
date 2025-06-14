package dev.langchain4j.community.store.embedding.typesense;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.List;
import org.typesense.api.FieldTypes;
import org.typesense.model.CollectionSchema;
import org.typesense.model.Field;

public class TypesenseSchema {

    private String idFieldName;
    private String textFieldName;
    private String embeddingFieldName;
    private String metadataFieldName;
    private String collectionName;
    private CollectionSchema collectionSchema;

    private TypesenseSchema(Builder builder) {
        this.idFieldName = ensureNotNull(builder.idFieldName, "idFieldName");
        this.textFieldName = ensureNotNull(builder.textFieldName, "textFieldName");
        this.embeddingFieldName = ensureNotNull(builder.embeddingFieldName, "embeddingFieldName");
        this.metadataFieldName = ensureNotNull(builder.metadataFieldName, "metadataFieldName");
        this.collectionName = ensureNotNull(builder.collectionName, "collectionName");
        this.collectionSchema = builder.collectionSchema;
    }

    public String getIdFieldName() {
        return idFieldName;
    }

    public void setIdFieldName(final String idFieldName) {
        this.idFieldName = idFieldName;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public void setTextFieldName(final String textFieldName) {
        this.textFieldName = textFieldName;
    }

    public String getEmbeddingFieldName() {
        return embeddingFieldName;
    }

    public void setEmbeddingFieldName(final String embeddingFieldName) {
        this.embeddingFieldName = embeddingFieldName;
    }

    public String getMetadataFieldName() {
        return metadataFieldName;
    }

    public void setMetadataFieldName(final String metadataFieldName) {
        this.metadataFieldName = metadataFieldName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(final String collectionName) {
        this.collectionName = collectionName;
    }

    public CollectionSchema getCollectionSchema() {
        return collectionSchema;
    }

    public void setCollectionSchema(final CollectionSchema collectionSchema) {
        this.collectionSchema = collectionSchema;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private static final String DEFAULT_ID_FIELD_NAME = "_id";
        private static final String DEFAULT_TEXT_FIELD_NAME = "text";
        private static final String DEFAULT_EMBEDDING_FIELD_NAME = "embedding";
        private static final String DEFAULT_METADATA_FIELD_NAME = "metadata";
        private static final String DEFAULT_COLLECTION_NAME = "langchain4j_collection";
        private static final int DEFAULT_DIMENSION = 384;
        private static final CollectionSchema DEFAULT_COLLECTION_SCHEMA = new CollectionSchema();

        static {
            List<Field> fields = List.of(
                    new Field()
                            .name(DEFAULT_ID_FIELD_NAME)
                            .type(FieldTypes.STRING)
                            .optional(false),
                    new Field()
                            .name(DEFAULT_TEXT_FIELD_NAME)
                            .type(FieldTypes.STRING)
                            .optional(true),
                    new Field()
                            .name(DEFAULT_EMBEDDING_FIELD_NAME)
                            .type(FieldTypes.FLOAT_ARRAY)
                            .numDim(DEFAULT_DIMENSION)
                            .optional(false),
                    new Field()
                            .name(DEFAULT_METADATA_FIELD_NAME)
                            .type(FieldTypes.OBJECT)
                            .optional(true));

            DEFAULT_COLLECTION_SCHEMA
                    .name(DEFAULT_COLLECTION_NAME)
                    .fields(fields)
                    .enableNestedFields(true);
        }

        private String idFieldName = DEFAULT_ID_FIELD_NAME;
        private String textFieldName = DEFAULT_TEXT_FIELD_NAME;
        private String embeddingFieldName = DEFAULT_EMBEDDING_FIELD_NAME;
        private String metadataFieldName = DEFAULT_METADATA_FIELD_NAME;
        private String collectionName = DEFAULT_COLLECTION_NAME;
        private CollectionSchema collectionSchema = DEFAULT_COLLECTION_SCHEMA;

        public Builder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        public Builder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        public Builder embeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
            return this;
        }

        public Builder metadataFieldName(String metadataFieldName) {
            this.metadataFieldName = metadataFieldName;
            return this;
        }

        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            this.collectionSchema.name(collectionName);
            return this;
        }

        public Builder collectionSchema(CollectionSchema collectionSchema) {
            this.collectionSchema = collectionSchema;
            return this;
        }

        public TypesenseSchema build() {
            return new TypesenseSchema(this);
        }
    }
}
