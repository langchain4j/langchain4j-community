package dev.langchain4j.community.store.embedding.yugabytedb;

/**
 * Metadata storage mode options for YugabyteDB embedding store.
 * <p>
 * Defines how metadata is stored and indexed in the database:
 * <ul>
 * <li>COLUMN_PER_KEY: Static metadata with dedicated columns for each key (best for known, fixed metadata schema)</li>
 * <li>COMBINED_JSON: Dynamic metadata stored as JSON (flexible but limited query performance)</li>
 * <li>COMBINED_JSONB: Dynamic metadata stored as binary JSON (flexible with better query performance and indexing)</li>
 * </ul>
 * <p>
 * Default value: COMBINED_JSONB
 */
public enum MetadataStorageMode {

    /**
     * COLUMN_PER_KEY: Static metadata with dedicated columns for each key.
     * <p>
     * Best for scenarios where:
     * - You know the metadata schema in advance
     * - You have a fixed set of metadata keys
     * - You need optimal query performance for specific metadata fields
     * - You want to leverage PostgreSQL's native column indexes
     * <p>
     * Example: User ID, document type, category - fields that are always present
     */
    COLUMN_PER_KEY,

    /**
     * COMBINED_JSON: Dynamic metadata stored as JSON.
     * <p>
     * Best for scenarios where:
     * - You have dynamic/unknown metadata schemas
     * - Metadata keys vary significantly between documents
     * - Storage efficiency is more important than query performance
     * - Simple text-based JSON querying is sufficient
     * - You do NOT need indexing capabilities
     * <p>
     * WARNING: Indexes are NOT supported for JSON storage mode.
     * Use COMBINED_JSONB instead if you need indexing capabilities.
     */
    COMBINED_JSON,

    /**
     * COMBINED_JSONB: Dynamic metadata stored as binary JSON (recommended).
     * <p>
     * Best for scenarios where:
     * - You have dynamic/unknown metadata schemas
     * - You need flexible querying with good performance
     * - You want to leverage PostgreSQL's advanced JSONB features
     * - You plan to create GIN indexes on metadata fields
     * <p>
     * This is the recommended mode for most use cases as it provides
     * the best balance of flexibility and performance.
     */
    COMBINED_JSONB
}
