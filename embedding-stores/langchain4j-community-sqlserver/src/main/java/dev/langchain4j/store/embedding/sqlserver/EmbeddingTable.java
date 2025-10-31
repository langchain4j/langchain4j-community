package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import javax.sql.DataSource;

/**
 * Represents a database table that stores embeddings, text segments, and metadata.
 * This class handles the creation and management of the table schema for SQL Server.
 */
public class EmbeddingTable {

    /**
     * The default catalog for the embedding table.
     */
    public static final String DEFAULT_CATALOG_NAME = null;

    /**
     * The default schema for the embedding table.
     */
    public static final String DEFAULT_SCHEMA_NAME = null;

    /**
     * The default name for the embedding table.
     */
    public static final String DEFAULT_TABLE_NAME = "embeddings";

    /**
     * The default name for the ID column.
     */
    public static final String DEFAULT_ID_COLUMN = "id";

    /**
     * The default name for the embedding column.
     */
    public static final String DEFAULT_EMBEDDING_COLUMN = "embedding";

    /**
     * The default name for the text column.
     */
    public static final String DEFAULT_TEXT_COLUMN = "text";

    /**
     * The default name for the metadata column.
     */
    public static final String DEFAULT_METADATA_COLUMN = "metadata";

    private final String catalogName;
    private final String schemaName;
    private final String tableName;
    private final String idColumn;
    private final String embeddingColumn;
    private final String textColumn;
    private final String metadataColumn;
    private final CreateOption createOption;
    private final Integer dimension;

    private EmbeddingTable(Builder builder) {
        this.catalogName = builder.catalogName;
        this.schemaName = builder.schemaName;
        this.tableName = builder.tableName;
        this.idColumn = builder.idColumn;
        this.embeddingColumn = builder.embeddingColumn;
        this.textColumn = builder.textColumn;
        this.metadataColumn = builder.metadataColumn;
        this.createOption = builder.createOption;
        this.dimension = builder.dimension;
        ensureNotNull(dimension, "dimension");
    }

    /**
     * Creates a builder for configuring an EmbeddingTable.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the name of the table.
     *
     * @return The table name.
     */
    public String name() {
        return tableName;
    }

    /**
     * Returns the name of the ID column.
     *
     * @return The ID column name.
     */
    public String idColumn() {
        return idColumn;
    }

    /**
     * Returns the name of the embedding column.
     *
     * @return The embedding column name.
     */
    public String embeddingColumn() {
        return embeddingColumn;
    }

    /**
     * Returns the name of the text column.
     *
     * @return The text column name.
     */
    public String textColumn() {
        return textColumn;
    }

    /**
     * Returns the name of the metadata column.
     *
     * @return The metadata column name.
     */
    public String metadataColumn() {
        return metadataColumn;
    }

    /**
     * Returns the dimension of the embedding vectors.
     *
     * @return The dimension, or null if not specified.
     */
    public Integer dimension() {
        return dimension;
    }

    /**
     * Creates the table in the database if the create option is configured to do so.
     *
     * @param dataSource The data source to use for database connections.
     * @throws SQLException If an error occurs while creating the table.
     */
    void create(DataSource dataSource) throws SQLException {
        ensureNotNull(dataSource, "dataSource");

        if (createOption == CreateOption.CREATE_NONE) {
            return;
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            if (createOption == CreateOption.CREATE_OR_REPLACE) {
                statement.addBatch(getDropTableStatement());
            }

            statement.addBatch(getCreateTableStatement());
            statement.executeBatch();
        }
    }

    /**
     * Maps a metadata key to a SQL expression for querying.
     *
     * @param key The metadata key.
     * @param sqlServerType The SQL Server type constant from java.sql.Types.
     * @return A SQL expression for accessing the metadata key.
     */
    public String mapMetadataKey(String key, int sqlServerType) {
        String jsonExpression = "JSON_VALUE(" + metadataColumn + ", '$." + key + '\'';

        // Add RETURNING clause based on SQL Server type
        if (sqlServerType == Types.DOUBLE) {
            jsonExpression += " RETURNING FLOAT";
        } else if (sqlServerType == Types.REAL) {
            jsonExpression += " RETURNING REAL";
        } else if (sqlServerType == Types.INTEGER) {
            jsonExpression += " RETURNING INT";
        } else if (sqlServerType == Types.BIGINT) {
            jsonExpression += " RETURNING BIGINT";
        } else if (sqlServerType == Types.BOOLEAN) {
            jsonExpression += " RETURNING BIT";
        }

        jsonExpression += ")";

        return jsonExpression;
    }

    /**
     * Constructs and returns the fully qualified name of the table, including catalog and schema names
     * if they are specified. If neither catalogName nor schemaName is provided, only tableName is returned.
     *
     * @return The fully qualified name of the table, composed of catalogName, schemaName, and tableName
     *         where applicable.
     */
    public String getQualifiedTableName() {
        if (catalogName == null && schemaName == null) {
            return tableName;
        } else if (catalogName == null) {
            return schemaName + '.' + tableName;
        } else {
            if (schemaName == null) {
                return catalogName + '.' + tableName;
            }
            return catalogName + '.' + schemaName + '.' + tableName;
        }
    }

    private String getCreateTableStatement() {
        return String.format(
                """
            CREATE TABLE %s (
                %s NVARCHAR(36) PRIMARY KEY,
                %s VECTOR(%d),
                %s NVARCHAR(MAX),
                %s JSON)
        """,
                getQualifiedTableName(), idColumn, embeddingColumn, dimension, textColumn, metadataColumn);
    }

    private String getDropTableStatement() {
        return "DROP TABLE IF EXISTS " + getQualifiedTableName();
    }

    /**
     * Builder class for creating EmbeddingTable instances.
     */
    public static class Builder {
        private String catalogName = DEFAULT_CATALOG_NAME;
        private String schemaName = DEFAULT_SCHEMA_NAME;
        private String tableName = DEFAULT_TABLE_NAME;
        private String idColumn = DEFAULT_ID_COLUMN;
        private String embeddingColumn = DEFAULT_EMBEDDING_COLUMN;
        private String textColumn = DEFAULT_TEXT_COLUMN;
        private String metadataColumn = DEFAULT_METADATA_COLUMN;
        private CreateOption createOption = CreateOption.CREATE_NONE;
        private Integer dimension;

        private Builder() {}

        /**
         * Sets the escaped catalog name.
         *
         * @param catalogName The escaped catalog name.
         * @return This builder.
         */
        public Builder catalogName(String catalogName) {
            this.catalogName = catalogName;
            return this;
        }

        /**
         * Sets the escaped schema name.
         *
         * @param schemaName The escaped schema name.
         * @return This builder.
         */
        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        /**
         * Sets the table name.
         *
         * @param tableName The table name.
         * @return This builder.
         */
        public Builder name(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Sets the ID column name.
         *
         * @param idColumn The ID column name.
         * @return This builder.
         */
        public Builder idColumn(String idColumn) {
            this.idColumn = idColumn;
            return this;
        }

        /**
         * Sets the embedding column name.
         *
         * @param embeddingColumn The embedding column name.
         * @return This builder.
         */
        public Builder embeddingColumn(String embeddingColumn) {
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        /**
         * Sets the text column name.
         *
         * @param textColumn The text column name.
         * @return This builder.
         */
        public Builder textColumn(String textColumn) {
            this.textColumn = textColumn;
            return this;
        }

        /**
         * Sets the metadata column name.
         *
         * @param metadataColumn The metadata column name.
         * @return This builder.
         */
        public Builder metadataColumn(String metadataColumn) {
            this.metadataColumn = metadataColumn;
            return this;
        }

        /**
         * Sets the create option.
         *
         * @param createOption The create option.
         * @return This builder.
         */
        public Builder createOption(CreateOption createOption) {
            this.createOption = createOption;
            return this;
        }

        /**
         * Sets the dimension of the embedding vectors.
         *
         * @param dimension The dimension.
         * @return This builder.
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * Builds the EmbeddingTable instance.
         *
         * @return The configured EmbeddingTable.
         */
        public EmbeddingTable build() {
            if (dimension == null) {
                throw new IllegalStateException("Dimension must be specified");
            }
            return new EmbeddingTable(this);
        }

        @Override
        public String toString() {
            return "Builder{" + "catalogName='"
                    + catalogName + '\'' + ", schemaName='"
                    + schemaName + '\'' + ", tableName='"
                    + tableName + '\'' + ", idColumn='"
                    + idColumn + '\'' + ", embeddingColumn='"
                    + embeddingColumn + '\'' + ", textColumn='"
                    + textColumn + '\'' + ", metadataColumn='"
                    + metadataColumn + '\'' + ", createOption="
                    + createOption + ", dimension="
                    + dimension + '}';
        }
    }
}
