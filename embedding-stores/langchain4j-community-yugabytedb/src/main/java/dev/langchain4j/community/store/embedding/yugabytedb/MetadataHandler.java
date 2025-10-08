package dev.langchain4j.community.store.embedding.yugabytedb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Interface for handling metadata storage and retrieval in YugabyteDB embedding store.
 * <p>
 * Different implementations provide different strategies for storing metadata:
 * - Column-based storage for static schemas
 * - JSON/JSONB storage for dynamic schemas
 * <p>
 * This design allows for flexible metadata handling while maintaining type safety
 * and optimal performance for different use cases.
 */
public interface MetadataHandler {

    /**
     * Returns the SQL column definitions for metadata storage.
     * <p>
     * Used during table creation to define the metadata columns.
     *
     * @return SQL column definitions string (e.g., "metadata JSONB" or "user_id UUID, category TEXT")
     */
    String columnDefinitionsString();

    /**
     * Creates indexes for metadata fields to optimize query performance.
     * <p>
     * Called during table initialization to create appropriate indexes
     * based on the storage mode and configuration.
     *
     * @param statement the SQL statement executor
     * @param table the table name
     * @throws SQLException if index creation fails
     */
    void createMetadataIndexes(Statement statement, String table) throws SQLException;

    /**
     * Returns the list of metadata column names.
     * <p>
     * Used for building SQL queries and parameter binding.
     *
     * @return list of column names used for metadata storage
     */
    List<String> columnsNames();

    /**
     * Generates a SQL WHERE clause from a filter.
     * <p>
     * Converts the filter object into SQL that's compatible with
     * the metadata storage strategy.
     *
     * @param filter the filter to convert
     * @return SQL WHERE clause with parameter placeholders
     */
    String whereClause(Filter filter);

    /**
     * Extracts metadata from a SQL result set.
     * <p>
     * Reconstructs the Metadata object from the database row
     * based on the storage strategy.
     *
     * @param resultSet the SQL result set
     * @return the reconstructed Metadata object
     * @throws SQLException if data extraction fails
     */
    Metadata fromResultSet(ResultSet resultSet) throws SQLException;

    /**
     * Generates the SQL fragment for INSERT/UPDATE operations.
     * <p>
     * Returns the part of the SQL that handles metadata during
     * upsert operations (ON CONFLICT ... DO UPDATE SET ...).
     *
     * @return SQL fragment for upsert operations
     */
    String insertClause();

    /**
     * Sets metadata values in a prepared statement.
     * <p>
     * Binds the metadata values to the prepared statement parameters
     * starting from the specified index.
     *
     * @param statement the prepared statement
     * @param parameterStartIndex the starting parameter index
     * @param metadata the metadata to bind
     * @return the next parameter index after binding metadata
     * @throws SQLException if parameter binding fails
     */
    int setMetadata(PreparedStatement statement, int parameterStartIndex, Metadata metadata) throws SQLException;

    /**
     * Sets filter parameters in a prepared statement.
     * <p>
     * Binds filter values to the prepared statement parameters
     * for WHERE clauses.
     *
     * @param statement the prepared statement
     * @param filter the filter containing values to bind
     * @param parameterStartIndex the starting parameter index
     * @return the next parameter index after binding filter parameters
     * @throws SQLException if parameter binding fails
     */
    int setFilterParameters(PreparedStatement statement, Filter filter, int parameterStartIndex) throws SQLException;
}
