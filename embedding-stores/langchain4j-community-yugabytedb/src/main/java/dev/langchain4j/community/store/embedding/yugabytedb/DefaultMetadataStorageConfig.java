package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of MetadataStorageConfig for YugabyteDB embedding store.
 * <p>
 * Provides sensible defaults for most use cases and supports all three storage modes.
 * Can be customized via the builder pattern for specific requirements.
 */
public class DefaultMetadataStorageConfig implements MetadataStorageConfig {

    private final MetadataStorageMode storageMode;
    private final List<String> columnDefinitions;
    private final List<String> indexes;
    private final String indexType;

    private DefaultMetadataStorageConfig(Builder builder) {
        this.storageMode = ensureNotNull(builder.storageMode, "storageMode");
        this.columnDefinitions = ensureNotEmpty(builder.columnDefinitions, "columnDefinitions");
        this.indexes = builder.indexes;
        this.indexType = builder.indexType;
    }

    @Override
    public MetadataStorageMode storageMode() {
        return storageMode;
    }

    @Override
    public List<String> columnDefinitions() {
        return columnDefinitions;
    }

    @Override
    public List<String> indexes() {
        return indexes;
    }

    @Override
    public String indexType() {
        return indexType;
    }

    /**
     * Creates a default configuration with COMBINED_JSONB storage mode.
     * This is the recommended configuration for most use cases.
     *
     * @return default metadata storage configuration
     */
    public static MetadataStorageConfig defaultConfig() {
        return builder().build();
    }

    /**
     * Creates a configuration for COLUMN_PER_KEY storage mode.
     *
     * @param columnDefinitions the SQL column definitions for metadata fields
     * @return metadata storage configuration for column-based storage
     */
    public static MetadataStorageConfig columnPerKey(List<String> columnDefinitions) {
        return builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(columnDefinitions)
                .indexes(Collections.emptyList()) // No default indexes for COLUMN_PER_KEY
                .indexType("BTREE")
                .build();
    }

    /**
     * Creates a configuration for COMBINED_JSON storage mode.
     * <p>
     * Note: JSON storage mode does not support indexing. Use COMBINED_JSONB for indexing support.
     *
     * @return metadata storage configuration for JSON storage
     */
    public static MetadataStorageConfig combinedJson() {
        return builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.emptyList()) // No indexes for JSON mode
                .indexType(null) // No index type for JSON mode
                .build();
    }

    /**
     * Creates a configuration for COMBINED_JSONB storage mode (recommended).
     *
     * @return metadata storage configuration for JSONB storage
     */
    public static MetadataStorageConfig combinedJsonb() {
        return builder()
                .storageMode(MetadataStorageMode.COMBINED_JSONB)
                .columnDefinitions(Collections.singletonList("metadata JSONB"))
                .indexes(Collections.singletonList("metadata"))
                .indexType("GIN")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MetadataStorageMode storageMode = MetadataStorageMode.COMBINED_JSONB;
        private List<String> columnDefinitions = Arrays.asList("metadata JSONB");
        private List<String> indexes = Arrays.asList("metadata");
        private String indexType = "GIN";

        public Builder storageMode(MetadataStorageMode storageMode) {
            this.storageMode = storageMode;
            return this;
        }

        public Builder columnDefinitions(List<String> columnDefinitions) {
            this.columnDefinitions = columnDefinitions;
            return this;
        }

        public Builder indexes(List<String> indexes) {
            this.indexes = indexes;
            return this;
        }

        public Builder indexType(String indexType) {
            this.indexType = indexType;
            return this;
        }

        public DefaultMetadataStorageConfig build() {
            return new DefaultMetadataStorageConfig(this);
        }
    }
}
