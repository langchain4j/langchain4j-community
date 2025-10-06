package dev.langchain4j.community.store.embedding.yugabytedb;

import java.util.Arrays;
import java.util.Collections;

/**
 * Example usage of different MetadataStorageMode configurations.
 * <p>
 * This class demonstrates how to configure the YugabyteDB embedding store
 * with different metadata storage strategies for different use cases.
 */
public class MetadataStorageModeExample {

    /**
     * Example configuration for COLUMN_PER_KEY storage mode.
     * <p>
     * Best for: Known, fixed metadata schema with optimal query performance.
     * Use case: User ID, document type, category - fields that are always present.
     */
    public static MetadataStorageConfig createColumnPerKeyConfig() {
        return DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList(
                        "user_id UUID NULL",
                        "document_type TEXT NULL",
                        "category TEXT NULL",
                        "priority INTEGER NULL",
                        "created_at TIMESTAMP NULL"))
                .indexes(Arrays.asList("user_id", "document_type", "category"))
                .indexType("BTREE")
                .build();
    }

    /**
     * Example configuration for COMBINED_JSON storage mode.
     * <p>
     * Best for: Dynamic metadata with simple querying needs.
     * Use case: Flexible metadata that doesn't require indexing.
     * <p>
     * WARNING: Indexes are NOT supported for JSON storage mode.
     * Use COMBINED_JSONB instead if you need indexing capabilities.
     */
    public static MetadataStorageConfig createCombinedJsonConfig() {
        return DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON NULL"))
                // DO NOT add indexes here - they are not supported for JSON mode
                // Use COMBINED_JSONB mode instead if you need indexing
                .build();
    }

    /**
     * Example configuration for COMBINED_JSONB storage mode (recommended).
     * <p>
     * Best for: Dynamic metadata with flexible querying and good performance.
     * Use case: Most applications - provides best balance of flexibility and performance.
     */
    public static MetadataStorageConfig createCombinedJsonbConfig() {
        return DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(Collections.singletonList("metadata JSONB NULL"))
                .indexes(Arrays.asList(
                        "metadata", // GIN index on entire JSONB column
                        "(metadata->>'user_id')", // Index on specific JSONB path
                        "(metadata->>'category')" // Index on another JSONB path
                        ))
                .indexType("GIN")
                .build();
    }

    /**
     * Example of how to create MetadataHandlers for different modes.
     */
    public static void demonstrateHandlerCreation() {
        // Column-based handler
        MetadataHandler columnHandler = MetadataHandlerFactory.create(createColumnPerKeyConfig());
        System.out.println("Column handler created: " + columnHandler.getClass().getSimpleName());

        // JSON handler
        MetadataHandler jsonHandler = MetadataHandlerFactory.create(createCombinedJsonConfig());
        System.out.println("JSON handler created: " + jsonHandler.getClass().getSimpleName());

        // JSONB handler (recommended)
        MetadataHandler jsonbHandler = MetadataHandlerFactory.create(createCombinedJsonbConfig());
        System.out.println("JSONB handler created: " + jsonbHandler.getClass().getSimpleName());
    }

    /**
     * Demonstrates what happens when you try to use indexes with JSON storage mode.
     * This will throw an IllegalArgumentException.
     */
    public static void demonstrateJsonIndexError() {
        try {
            // This will fail because indexes are not supported for JSON mode
            MetadataStorageConfig invalidJsonConfig = DefaultMetadataStorageConfig.builder()
                    .storageMode(MetadataStorageMode.COMBINED_JSON)
                    .columnDefinitions(Collections.singletonList("metadata JSON NULL"))
                    .indexes(Arrays.asList("metadata")) // This will cause an error
                    .build();

            MetadataHandlerFactory.create(invalidJsonConfig);
            System.out.println("ERROR: This should not print - indexes should be rejected!");

        } catch (IllegalArgumentException e) {
            System.out.println("✅ Expected error caught: " + e.getMessage());
            System.out.println("💡 Solution: Use COMBINED_JSONB mode for indexing support");
        }
    }
}
