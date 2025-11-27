package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * <p>
 *   Represents a database index. Indexes can be configured using the builders:
 * </p>
 *   <ul>
 *     <li>{@link JSONIndexBuilder}</li>
 *   </ul>
 * <p>
 *   {@link JSONIndexBuilder} allows to configure JSON indexes on the metadata
 *   column using SQL Server 2025 JSON index functionality.
 * </p>
 */
public class Index {

    /**
     * The index builder.
     */
    private final IndexBuilder builder;

    /**
     * The name of the table.
     */
    private String tableName;

    /**
     * Create an index.
     * @param builder The builder.
     */
    Index(IndexBuilder builder) {
        this.builder = builder;
    }

    /**
     * Creates a builder to configure a JSON index on the metadata column of
     * the {@link EmbeddingTable}.
     * @return A builder that allows to configure a JSON index.
     */
    public static JSONIndexBuilder jsonIndexBuilder() {
        return new JSONIndexBuilder();
    }

    /**
     * Returns the name of the index.
     *
     * @return The name of the index or null if the name has not been set and the index
     * has not been created.
     */
    public String name() {
        return builder.indexName;
    }

    /**
     * Returns the name of this table.
     *
     * @return Once the index has been created it returns the table name, otherwise it
     * returns null.
     */
    public String tableName() {
        return tableName;
    }

    /**
     * Creates the index.
     * @param dataSource The datasource.
     * @param embeddingTable The embedding table.
     * @throws SQLException If an error occurs while creating the index.
     */
    void create(DataSource dataSource, EmbeddingTable embeddingTable) throws SQLException {

        ensureNotNull(dataSource, "dataSource");
        ensureNotNull(embeddingTable, "embeddingTable");

        this.tableName = embeddingTable.getQualifiedTableName();

        if (builder.createOption == CreateOption.CREATE_NONE) return;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (builder.createOption == CreateOption.CREATE_OR_REPLACE) {
                statement.addBatch(builder.getDropIndexStatement(embeddingTable));
            }
            statement.addBatch(builder.getCreateIndexStatement(embeddingTable));
            statement.executeBatch();
        }
    }
}
