package dev.langchain4j.community.store.embedding.typesense;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Represent Typesense settings.
 *
 * @param collectionName     Collection name. (Optional) Default value: langchain4j_collection.
 * @param idFieldName        Field name of id. (Optional) Default value: _id.
 * @param textFieldName      Field name of text. (Optional) Default value: text.
 * @param embeddingFieldName Field name of embedding. (Optional) Default value: embedding.
 * @param metadataFieldName  Field name of metadata. (Optional) Default value: metadata.
 * @param dimension          Dimension of embedding to store. Required if you want langchain4j to create collection automatically.
 */
public record TypesenseSettings(
        String collectionName,
        String idFieldName,
        String textFieldName,
        String embeddingFieldName,
        String metadataFieldName,
        Integer dimension) {

    static String DEFAULT_COLLECTION_NAME = "langchain4j_collection";

    static String DEFAULT_ID_FIELD_NAME = "_id";

    static String DEFAULT_TEXT_FIELD_NAME = "text";

    static String DEFAULT_EMBEDDING_FIELD_NAME = "embedding";

    static String DEFAULT_METADATA_FIELD_NAME = "metadata";

    public TypesenseSettings {
        collectionName = getOrDefault(collectionName, DEFAULT_COLLECTION_NAME);
        idFieldName = getOrDefault(idFieldName, DEFAULT_ID_FIELD_NAME);
        textFieldName = getOrDefault(textFieldName, DEFAULT_TEXT_FIELD_NAME);
        embeddingFieldName = getOrDefault(embeddingFieldName, DEFAULT_EMBEDDING_FIELD_NAME);
        metadataFieldName = getOrDefault(metadataFieldName, DEFAULT_METADATA_FIELD_NAME);
    }

    public TypesenseSettings() {
        this(null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String collectionName;
        private String idFieldName;
        private String textFieldName;
        private String embeddingFieldName;
        private String metadataFieldName;
        private Integer dimension;

        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

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

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public TypesenseSettings build() {
            return new TypesenseSettings(
                    collectionName, idFieldName, textFieldName, embeddingFieldName, metadataFieldName, dimension);
        }
    }
}
