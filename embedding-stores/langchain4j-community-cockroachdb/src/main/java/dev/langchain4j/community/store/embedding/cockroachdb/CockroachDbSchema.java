package dev.langchain4j.community.store.embedding.cockroachdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.community.store.embedding.cockroachdb.index.BaseIndex;
import dev.langchain4j.community.store.embedding.cockroachdb.index.NoIndex;
import java.util.Collections;
import java.util.List;

/**
 * Schema configuration for a CockroachDB embedding table.
 *
 * <p>Defaults match the column layout used by the Python
 * {@code langchain-cockroachdb} library:
 *
 * <pre>
 *   CREATE TABLE &lt;table&gt; (
 *     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *     [namespace TEXT NOT NULL DEFAULT '',]
 *     content TEXT,
 *     embedding VECTOR(&lt;dim&gt;),
 *     metadata JSONB DEFAULT '{}'::jsonb,
 *     created_at TIMESTAMPTZ DEFAULT now(),
 *     [content_tsvector TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED]
 *   );
 * </pre>
 */
public class CockroachDbSchema {

    public static final String DEFAULT_TABLE_NAME = "embeddings";
    public static final String DEFAULT_SCHEMA_NAME = "public";
    public static final String DEFAULT_ID_COLUMN = "id";
    public static final String DEFAULT_CONTENT_COLUMN = "content";
    public static final String DEFAULT_EMBEDDING_COLUMN = "embedding";
    public static final String DEFAULT_METADATA_COLUMN = "metadata";
    public static final String DEFAULT_NAMESPACE_COLUMN = "namespace";

    private final String tableName;
    private final String schemaName;
    private final String idColumn;
    private final String contentColumn;
    private final String embeddingColumn;
    private final String metadataColumn;
    private final String namespaceColumn; // null disables multi-tenancy

    private final Integer dimension;
    private final MetricType metricType;
    private final BaseIndex vectorIndex;
    private final boolean createTableIfNotExists;
    private final boolean createTsvectorColumn;
    private final String tsvectorLanguage;

    private CockroachDbSchema(Builder b) {
        this.tableName = ensureNotBlank(b.tableName, "tableName");
        this.schemaName = b.schemaName;
        this.idColumn = ensureNotBlank(b.idColumn, "idColumn");
        this.contentColumn = ensureNotBlank(b.contentColumn, "contentColumn");
        this.embeddingColumn = ensureNotBlank(b.embeddingColumn, "embeddingColumn");
        this.metadataColumn = ensureNotBlank(b.metadataColumn, "metadataColumn");
        this.namespaceColumn = b.namespaceColumn;
        this.dimension = ensureNotNull(b.dimension, "dimension");
        this.metricType = ensureNotNull(b.metricType, "metricType");
        this.vectorIndex = b.vectorIndex != null ? b.vectorIndex : new NoIndex();
        this.createTableIfNotExists = b.createTableIfNotExists;
        this.createTsvectorColumn = b.createTsvectorColumn;
        this.tsvectorLanguage = ensureNotBlank(b.tsvectorLanguage, "tsvectorLanguage");
        ensureTrue(dimension > 0, "dimension must be positive");
    }

    public static Builder builder() {
        return new Builder();
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

    public String getEmbeddingColumn() {
        return embeddingColumn;
    }

    public String getMetadataColumn() {
        return metadataColumn;
    }

    public String getNamespaceColumn() {
        return namespaceColumn;
    }

    public boolean hasNamespace() {
        return namespaceColumn != null && !namespaceColumn.isEmpty();
    }

    public Integer getDimension() {
        return dimension;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public BaseIndex getVectorIndex() {
        return vectorIndex;
    }

    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    public boolean isCreateTsvectorColumn() {
        return createTsvectorColumn;
    }

    public String getTsvectorLanguage() {
        return tsvectorLanguage;
    }

    public String getFullTableName() {
        return schemaName != null && !schemaName.trim().isEmpty() ? schemaName + "." + tableName : tableName;
    }

    public String getTsvectorColumn() {
        return contentColumn + "_tsvector";
    }

    public String getCreateTableSql() {
        StringBuilder sql = new StringBuilder()
                .append("CREATE TABLE IF NOT EXISTS ").append(getFullTableName()).append(" (")
                .append(idColumn).append(" UUID PRIMARY KEY DEFAULT gen_random_uuid(), ");
        if (hasNamespace()) {
            sql.append(namespaceColumn).append(" TEXT NOT NULL DEFAULT '', ");
        }
        sql.append(contentColumn).append(" TEXT, ")
                .append(embeddingColumn).append(" VECTOR(").append(dimension).append("), ")
                .append(metadataColumn).append(" JSONB DEFAULT '{}'::jsonb, ")
                .append("created_at TIMESTAMPTZ DEFAULT now()")
                .append(")");
        return sql.toString();
    }

    public String getCreateNamespaceIndexSql() {
        if (!hasNamespace()) return null;
        return String.format(
                "CREATE INDEX IF NOT EXISTS %s_%s_idx ON %s (%s)",
                tableName, namespaceColumn, getFullTableName(), namespaceColumn);
    }

    public String getAddTsvectorColumnSql() {
        if (!createTsvectorColumn) return null;
        return String.format(
                "ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s TSVECTOR "
                        + "GENERATED ALWAYS AS (to_tsvector('%s', %s)) STORED",
                getFullTableName(), getTsvectorColumn(), tsvectorLanguage, contentColumn);
    }

    public String getCreateTsvectorIndexSql() {
        if (!createTsvectorColumn) return null;
        return String.format(
                "CREATE INDEX IF NOT EXISTS %s_%s_idx ON %s USING GIN (%s)",
                tableName, getTsvectorColumn(), getFullTableName(), getTsvectorColumn());
    }

    public String getCreateVectorIndexSql() {
        List<String> prefix = hasNamespace() ? Collections.singletonList(namespaceColumn) : Collections.emptyList();
        return vectorIndex.getCreateIndexSql(getFullTableName(), embeddingColumn, prefix);
    }

    public static class Builder {
        private String tableName = DEFAULT_TABLE_NAME;
        private String schemaName = DEFAULT_SCHEMA_NAME;
        private String idColumn = DEFAULT_ID_COLUMN;
        private String contentColumn = DEFAULT_CONTENT_COLUMN;
        private String embeddingColumn = DEFAULT_EMBEDDING_COLUMN;
        private String metadataColumn = DEFAULT_METADATA_COLUMN;
        private String namespaceColumn; // null = disabled
        private Integer dimension;
        private MetricType metricType = MetricType.COSINE;
        private BaseIndex vectorIndex;
        private boolean createTableIfNotExists = true;
        private boolean createTsvectorColumn = false;
        private String tsvectorLanguage = "english";

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

        public Builder embeddingColumn(String embeddingColumn) {
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        public Builder metadataColumn(String metadataColumn) {
            this.metadataColumn = metadataColumn;
            return this;
        }

        /** Pass non-null to scope rows by tenant; pass {@link #DEFAULT_NAMESPACE_COLUMN} for the conventional name. */
        public Builder namespaceColumn(String namespaceColumn) {
            this.namespaceColumn = namespaceColumn;
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

        public Builder vectorIndex(BaseIndex vectorIndex) {
            this.vectorIndex = vectorIndex;
            return this;
        }

        public Builder createTableIfNotExists(boolean createTableIfNotExists) {
            this.createTableIfNotExists = createTableIfNotExists;
            return this;
        }

        /** Adds a generated TSVECTOR column + GIN index for future hybrid search. */
        public Builder createTsvectorColumn(boolean createTsvectorColumn) {
            this.createTsvectorColumn = createTsvectorColumn;
            return this;
        }

        public Builder tsvectorLanguage(String tsvectorLanguage) {
            this.tsvectorLanguage = tsvectorLanguage;
            return this;
        }

        public CockroachDbSchema build() {
            return new CockroachDbSchema(this);
        }
    }
}
