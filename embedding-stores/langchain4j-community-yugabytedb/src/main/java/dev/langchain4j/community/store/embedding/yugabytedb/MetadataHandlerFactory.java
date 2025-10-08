package dev.langchain4j.community.store.embedding.yugabytedb;

/**
 * Factory for creating appropriate MetadataHandler instances based on storage configuration.
 * <p>
 * Determines the optimal metadata handler implementation based on the storage mode
 * and configuration provided. This allows the system to use different strategies
 * for different use cases while maintaining a consistent interface.
 */
public class MetadataHandlerFactory {

    /**
     * Creates a MetadataHandler based on the provided configuration.
     *
     * @param config the metadata storage configuration
     * @return an appropriate MetadataHandler implementation
     * @throws IllegalArgumentException if the storage mode is not supported
     */
    public static MetadataHandler create(MetadataStorageConfig config) {
        if (config == null || config.storageMode() == null) {
            // Use default JSONB configuration
            config = DefaultMetadataStorageConfig.defaultConfig();
        }

        return switch (config.storageMode()) {
            case COMBINED_JSONB -> new JsonbMetadataHandler(config);
            case COMBINED_JSON -> new JsonMetadataHandler(config);
            case COLUMN_PER_KEY -> new ColumnMetadataHandler(config);
            default -> throw new IllegalArgumentException("Unsupported storage mode: " + config.storageMode());
        };
    }
}
