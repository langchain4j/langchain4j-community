package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.community.store.embedding.yugabytedb.MetricType.COSINE;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.community.store.embedding.yugabytedb.index.BaseIndex;
import dev.langchain4j.community.store.embedding.yugabytedb.index.NoIndex;

/**
 * YugabyteDB Schema Configuration
 *
 * This class encapsulates the database schema configuration for YugabyteDB embedding storage,
 * including table structure, column names, indexing strategy, and vector configuration.
 */
public class YugabyteDBSchema {

    /* Default column names */
    public static final String DEFAULT_TABLE_NAME = "embeddings";
    public static final String DEFAULT_ID_COLUMN = "id";
    public static final String DEFAULT_CONTENT_COLUMN = "content";
    public static final String DEFAULT_METADATA_COLUMN = "metadata";
    public static final String DEFAULT_EMBEDDING_COLUMN = "embedding";
    public static final String DEFAULT_SCHEMA_NAME = "public";

    /* Index configuration */
    private static final MetricType DEFAULT_METRIC_TYPE = COSINE;

    /* Schema configuration */
    private final String tableName;
    private final String schemaName;
    private final String idColumn;
    private final String contentColumn;
    private final String metadataColumn;
    private final String embeddingColumn;

    /* Vector configuration */
    private final Integer dimension;
    private final MetricType metricType;
    private final BaseIndex vectorIndex;
    private final boolean createTableIfNotExists;

    private YugabyteDBSchema(Builder builder) {
        this.tableName = ensureNotBlank(builder.tableName, "tableName");
        this.schemaName = builder.schemaName; // this can be empty because default schema is public
        this.idColumn = ensureNotBlank(builder.idColumn, "idColumn");
        this.contentColumn = ensureNotBlank(builder.contentColumn, "contentColumn");
        this.metadataColumn = ensureNotBlank(builder.metadataColumn, "metadataColumn");
        this.embeddingColumn = ensureNotBlank(builder.embeddingColumn, "embeddingColumn");
        this.dimension = ensureNotNull(builder.dimension, "dimension");
        this.metricType = ensureNotNull(builder.metricType, "metricType");

        // Use provided index or default to NoIndex
        this.vectorIndex = builder.vectorIndex != null ? builder.vectorIndex : new NoIndex();
        this.createTableIfNotExists = builder.createTableIfNotExists;

        ensureTrue(dimension > 0, "dimension must be positive");
    }

    public String getTableName() {
        return tableName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public String getContentColumn() {
        return contentColumn;
    }

    public String getMetadataColumn() {
        return metadataColumn;
    }

    public String getEmbeddingColumn() {
        return embeddingColumn;
    }

    public Integer getDimension() {
        return dimension;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    /**
     * Get the vector index configuration.
     *
     * @return the vector index
     */
    public BaseIndex getVectorIndex() {
        return vectorIndex;
    }

    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    /**
     * Get the full table name including schema if specified
     */
    public String getFullTableName() {
        return schemaName != null && !schemaName.trim().isEmpty() ? schemaName + "." + tableName : tableName;
    }

    /**
     * Get the distance operator for the metric type
     */
    public String getDistanceOperator() {
        switch (metricType) {
            case COSINE:
                return "vector_cosine_ops";
            case EUCLIDEAN:
                return "vector_l2_ops";
            case DOT_PRODUCT:
                return "vector_ip_ops";
            default:
                return "vector_cosine_ops";
        }
    }

    /**
     * Get the distance function for queries
     */
    public String getDistanceFunction() {
        switch (metricType) {
            case COSINE:
                return "<=>";
            case EUCLIDEAN:
                return "<->";
            case DOT_PRODUCT:
                return "<#>";
            default:
                return "<=>";
        }
    }

    /**
     * Generate CREATE TABLE SQL
     */
    public String getCreateTableSql() {
        return String.format(
                "CREATE TABLE IF NOT EXISTS %s (" + "%s UUID PRIMARY KEY, "
                        + "%s TEXT, "
                        + "%s JSONB, "
                        + "%s vector(%d)"
                        + ")",
                getFullTableName(), idColumn, contentColumn, metadataColumn, embeddingColumn, dimension);
    }

    /**
     * Generate CREATE INDEX SQL
     */
    public String getCreateIndexSql() {
        if (vectorIndex == null || vectorIndex instanceof NoIndex) {
            return null; // Skip index creation for basic YugabyteDB compatibility
        }

        // Generate index name
        String indexName = vectorIndex.getName();
        if (indexName == null || indexName.isEmpty()) {
            indexName = tableName + "_" + embeddingColumn + "_idx";
        }

        // Build CREATE INDEX SQL with index-specific options
        String indexOptions = vectorIndex.getIndexOptions();
        if (indexOptions != null && !indexOptions.isEmpty()) {
            return String.format(
                    "CREATE INDEX IF NOT EXISTS %s ON %s USING %s (%s %s) WITH %s",
                    indexName,
                    getFullTableName(),
                    vectorIndex.getIndexType(),
                    embeddingColumn,
                    getDistanceOperator(),
                    indexOptions);
        } else {
            return String.format(
                    "CREATE INDEX IF NOT EXISTS %s ON %s USING %s (%s %s)",
                    indexName, getFullTableName(), vectorIndex.getIndexType(), embeddingColumn, getDistanceOperator());
        }
    }

    /**
     * Generate INSERT SQL with upsert capability
     */
    public String getInsertSql() {
        return String.format(
                "INSERT INTO %s (%s, %s, %s, %s) VALUES (?::uuid, ?, ?::jsonb, ?::vector) "
                        + "ON CONFLICT (%s) DO UPDATE SET %s = EXCLUDED.%s, %s = EXCLUDED.%s, %s = EXCLUDED.%s",
                getFullTableName(),
                idColumn,
                contentColumn,
                metadataColumn,
                embeddingColumn,
                idColumn,
                contentColumn,
                contentColumn,
                metadataColumn,
                metadataColumn,
                embeddingColumn,
                embeddingColumn);
    }

    /**
     * Generate DELETE SQL with proper UUID casting
     */
    public String getDeleteSql() {
        String tableName = getFullTableName();
        return String.format("DELETE FROM %s WHERE %s = ?::uuid", tableName, idColumn);
    }

    /**
     * Generate DELETE ALL SQL
     */
    public String getDeleteAllSql() {
        String tableName = getFullTableName();
        return String.format("DELETE FROM %s", tableName);
    }

    /**
     * Generate SELECT SQL for similarity search
     */
    public String getSearchSql() {
        String tableName = getFullTableName();
        String distanceFunction = getDistanceFunction();
        return String.format(
                "SELECT %s, %s, %s, %s %s ? AS distance FROM %s",
                idColumn, contentColumn, metadataColumn, embeddingColumn, distanceFunction, tableName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tableName = DEFAULT_TABLE_NAME;
        private String schemaName = DEFAULT_SCHEMA_NAME;
        private String idColumn = DEFAULT_ID_COLUMN;
        private String contentColumn = DEFAULT_CONTENT_COLUMN;
        private String metadataColumn = DEFAULT_METADATA_COLUMN;
        private String embeddingColumn = DEFAULT_EMBEDDING_COLUMN;
        private Integer dimension;
        private MetricType metricType = DEFAULT_METRIC_TYPE;
        private BaseIndex vectorIndex;
        private boolean createTableIfNotExists = true;

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder idColumn(String idColumn) {
            this.idColumn = idColumn;
            return this;
        }

        public Builder contentColumn(String contentColumn) {
            this.contentColumn = contentColumn;
            return this;
        }

        public Builder metadataColumn(String metadataColumn) {
            this.metadataColumn = metadataColumn;
            return this;
        }

        public Builder embeddingColumn(String embeddingColumn) {
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        /**
         * Set the vector index configuration.
         * Use this for type-safe index configuration with specific parameters.
         *
         * <p><b>YugabyteDB supported indexes:</b></p>
         * <ul>
         *   <li>HNSW (ybhnsw) - Recommended for approximate nearest neighbor search</li>
         *   <li>NoIndex - Sequential scan for exact search (slower)</li>
         * </ul>
         *
         * <p>Examples:</p>
         * <pre>
         * // HNSW index with custom parameters (recommended)
         * .vectorIndex(HNSWIndex.builder()
         *     .m(16)
         *     .efConstruction(64)
         *     .metricType(MetricType.COSINE)
         *     .build())
         *
         * // No index (sequential scan)
         * .vectorIndex(new NoIndex())
         * </pre>
         *
         * @param vectorIndex the vector index configuration
         * @return this builder
         */
        public Builder vectorIndex(BaseIndex vectorIndex) {
            this.vectorIndex = vectorIndex;
            return this;
        }

        public Builder createTableIfNotExists(boolean createTableIfNotExists) {
            this.createTableIfNotExists = createTableIfNotExists;
            return this;
        }

        public YugabyteDBSchema build() {
            return new YugabyteDBSchema(this);
        }
    }
}
