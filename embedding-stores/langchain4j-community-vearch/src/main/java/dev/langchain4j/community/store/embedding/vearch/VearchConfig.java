package dev.langchain4j.community.store.embedding.vearch;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.community.store.embedding.vearch.field.Field;
import dev.langchain4j.community.store.embedding.vearch.field.FieldType;
import dev.langchain4j.community.store.embedding.vearch.field.StringField;
import dev.langchain4j.community.store.embedding.vearch.field.VectorField;
import dev.langchain4j.community.store.embedding.vearch.index.HNSWParam;
import dev.langchain4j.community.store.embedding.vearch.index.Index;
import dev.langchain4j.community.store.embedding.vearch.index.IndexType;
import dev.langchain4j.community.store.embedding.vearch.index.search.SearchIndexParam;
import java.util.List;

public class VearchConfig {

    static final String DEFAULT_ID_FIELD_NAME = "_id";
    static final String DEFAULT_EMBEDDING_FIELD_NAME = "embedding";
    static final String DEFAULT_TEXT_FIELD_NAME = "text";
    static final String DEFAULT_SCORE_FILED_NAME = "_score";

    private String databaseName;
    private String spaceName;

    /* Attributes for creating space */

    private int replicaNum;
    private int partitionNum;

    /* Attributes for searching */

    /**
     * Index param when searching, if not set, will use {@link Index}.
     *
     * @see Index
     */
    private SearchIndexParam searchIndexParam;
    /**
     * This attribute's key set should contain
     * {@link VearchConfig#embeddingFieldName}, {@link VearchConfig#textFieldName} and {@link VearchConfig#metadataFieldNames}
     */
    private List<Field> fields;

    private String embeddingFieldName;
    private String textFieldName;
    /**
     * This attribute should be the subset of {@link VearchConfig#fields}'s key set
     */
    private List<String> metadataFieldNames;

    public VearchConfig(Builder builder) {
        this.databaseName = ensureNotNull(builder.databaseName, "databaseName");
        this.spaceName = ensureNotNull(builder.spaceName, "spaceName");
        this.replicaNum = getOrDefault(builder.replicaNum, 1);
        this.partitionNum = getOrDefault(builder.partitionNum, 1);
        this.searchIndexParam = builder.searchIndexParam;
        this.embeddingFieldName = getOrDefault(builder.embeddingFieldName, DEFAULT_EMBEDDING_FIELD_NAME);
        this.textFieldName = getOrDefault(builder.textFieldName, DEFAULT_TEXT_FIELD_NAME);
        this.metadataFieldNames = builder.metadataFieldNames;
        this.fields = getOrDefault(builder.fields, toFields(builder.dimension));
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public int getReplicaNum() {
        return replicaNum;
    }

    public void setReplicaNum(int replicaNum) {
        this.replicaNum = replicaNum;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(int partitionNum) {
        this.partitionNum = partitionNum;
    }

    public SearchIndexParam getSearchIndexParam() {
        return searchIndexParam;
    }

    public void setSearchIndexParam(SearchIndexParam searchIndexParam) {
        this.searchIndexParam = searchIndexParam;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public String getEmbeddingFieldName() {
        return embeddingFieldName;
    }

    public void setEmbeddingFieldName(String embeddingFieldName) {
        this.embeddingFieldName = embeddingFieldName;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public void setTextFieldName(String textFieldName) {
        this.textFieldName = textFieldName;
    }

    public List<String> getMetadataFieldNames() {
        return metadataFieldNames;
    }

    public void setMetadataFieldNames(List<String> metadataFieldNames) {
        this.metadataFieldNames = metadataFieldNames;
    }

    private List<Field> toFields(Integer dimension) {

        // Construct default fields without metadata
        return List.of(
                VectorField.builder()
                        .name(embeddingFieldName)
                        .dimension(dimension)
                        .index(Index.builder()
                                .name("gamma")
                                .type(IndexType.HNSW)
                                .params(HNSWParam.builder()
                                        .metricType(MetricType.INNER_PRODUCT)
                                        .efConstruction(100)
                                        .nLinks(32)
                                        .efSearch(64)
                                        .build())
                                .build())
                        .build(),
                StringField.builder()
                        .name(textFieldName)
                        .fieldType(FieldType.STRING)
                        .build());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String databaseName;
        private String spaceName;
        private Integer dimension;
        private Integer replicaNum;
        private Integer partitionNum;
        private SearchIndexParam searchIndexParam;
        private List<Field> fields;
        private String embeddingFieldName;
        private String textFieldName;
        private List<String> metadataFieldNames;

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder spaceName(String spaceName) {
            this.spaceName = spaceName;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder replicaNum(Integer replicaNum) {
            this.replicaNum = replicaNum;
            return this;
        }

        public Builder partitionNum(Integer partitionNum) {
            this.partitionNum = partitionNum;
            return this;
        }

        public Builder searchIndexParam(SearchIndexParam searchIndexParam) {
            this.searchIndexParam = searchIndexParam;
            return this;
        }

        public Builder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder embeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
            return this;
        }

        public Builder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        public Builder metadataFieldNames(List<String> metadataFieldNames) {
            this.metadataFieldNames = metadataFieldNames;
            return this;
        }

        public VearchConfig build() {
            return new VearchConfig(this);
        }
    }
}
