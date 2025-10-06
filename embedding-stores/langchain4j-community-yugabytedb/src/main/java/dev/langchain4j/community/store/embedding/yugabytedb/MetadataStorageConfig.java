package dev.langchain4j.community.store.embedding.yugabytedb;

import java.util.List;

/**
 * Configuration interface for metadata storage in YugabyteDB embedding store.
 * <p>
 * Defines how metadata should be stored, indexed, and queried in the database.
 * Different implementations support different storage strategies for optimal
 * performance based on your use case.
 */
public interface MetadataStorageConfig {

    /**
     * The metadata storage mode defining the overall storage strategy.
     *
     * @return the storage mode (COLUMN_PER_KEY, COMBINED_JSON, or COMBINED_JSONB)
     */
    MetadataStorageMode storageMode();

    /**
     * SQL column definitions for metadata storage.
     * <p>
     * Examples:
     * <ul>
     * <li>COMBINED_JSON: {@code ["metadata JSON"]} </li>
     * <li>COMBINED_JSONB: {@code ["metadata JSONB"]} </li>
     * <li>COLUMN_PER_KEY: {@code ["user_id UUID", "category TEXT", "priority INTEGER"]} </li>
     * </ul>
     *
     * @return list of SQL column definitions
     */
    List<String> columnDefinitions();

    /**
     * Index definitions for optimizing metadata queries.
     * <p>
     * Examples:
     * <ul>
     * <li>COMBINED_JSON: {@code ["metadata"]} for basic index or {@code ["(metadata->>'category')"]} for specific paths</li>
     * <li>COMBINED_JSONB: {@code ["metadata"]} for GIN index or {@code ["(metadata->>'user_id')"]} for specific paths</li>
     * <li>COLUMN_PER_KEY: {@code ["user_id", "category"]} for column indexes</li>
     * </ul>
     *
     * @return list of index definitions
     */
    List<String> indexes();

    /**
     * The index type to use for metadata indexes.
     * <p>
     * Common options:
     * <ul>
     * <li>BTREE: Standard index for equality and range queries (default for columns)</li>
     * <li>GIN: Generalized Inverted Index, optimal for JSONB queries</li>
     * <li>HASH: Hash index for equality queries only</li>
     * </ul>
     *
     * @return the index type (e.g., "BTREE", "GIN", "HASH")
     */
    String indexType();
}
