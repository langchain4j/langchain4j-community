package dev.langchain4j.community.store.embedding.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Test to verify MetadataStorageConfig implementations work correctly,
 * especially the JSON index validation fix.
 */
class MetadataStorageConfigTest {

    @Test
    void defaultConfig() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.defaultConfig();

        assertThat(config.storageMode()).isEqualTo(MetadataStorageMode.COMBINED_JSONB);
        assertThat(config.columnDefinitions()).isEqualTo(Collections.singletonList("metadata JSONB"));
        assertThat(config.indexes()).isEqualTo(Collections.singletonList("metadata"));
        assertThat(config.indexType()).isEqualTo("GIN");
    }

    @Test
    void combinedJsonbConfig() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJsonb();

        assertThat(config.storageMode()).isEqualTo(MetadataStorageMode.COMBINED_JSONB);
        assertThat(config.columnDefinitions()).isEqualTo(Collections.singletonList("metadata JSONB"));
        assertThat(config.indexes()).isEqualTo(Collections.singletonList("metadata"));
        assertThat(config.indexType()).isEqualTo("GIN");
    }

    @Test
    void combinedJsonConfigNoIndexes() {
        // This should NOT throw an error anymore (fixed)
        MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJson();

        assertThat(config.storageMode()).isEqualTo(MetadataStorageMode.COMBINED_JSON);
        assertThat(config.columnDefinitions()).isEqualTo(Collections.singletonList("metadata JSON"));
        assertThat(config.indexes()).isEqualTo(Collections.emptyList()); // ✅ Fixed: No indexes
        assertThat(config.indexType()).isNull(); // ✅ Fixed: No index type
    }

    @Test
    void columnPerKeyConfig() {
        MetadataStorageConfig config =
                DefaultMetadataStorageConfig.columnPerKey(Arrays.asList("user_id UUID", "category TEXT"));

        assertThat(config.storageMode()).isEqualTo(MetadataStorageMode.COLUMN_PER_KEY);
        assertThat(config.columnDefinitions()).isEqualTo(Arrays.asList("user_id UUID", "category TEXT"));
        assertThat(config.indexType()).isEqualTo("BTREE");
    }

    @Test
    void jsonHandlerCreationNoError() {
        // This should work now (after the fix)
        MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJson();

        // Should not throw an exception
        assertDoesNotThrow(() -> {
            MetadataHandler handler = MetadataHandlerFactory.create(config);
            assertThat(handler)
                    .isInstanceOf(JsonMetadataHandler.class);
        });
    }

    @Test
    void jsonHandlerWithManualIndexesShouldFail() {
        // This should still fail if someone manually adds indexes to JSON config
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("metadata")) // This should cause failure
                .build();

        // Should throw IllegalArgumentException
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> MetadataHandlerFactory.create(config));
    }

    @Test
    void allThreeHandlerTypes() {
        // Test that all three storage modes work

        // 1. Column-based
        MetadataStorageConfig columnConfig =
                DefaultMetadataStorageConfig.columnPerKey(Arrays.asList("user_id UUID", "category TEXT"));
        MetadataHandler columnHandler = MetadataHandlerFactory.create(columnConfig);
        assertThat(columnHandler).isInstanceOf(ColumnMetadataHandler.class);

        // 2. JSON (fixed)
        MetadataStorageConfig jsonConfig = DefaultMetadataStorageConfig.combinedJson();
        MetadataHandler jsonHandler = MetadataHandlerFactory.create(jsonConfig);
        assertThat(jsonHandler).isInstanceOf(JsonMetadataHandler.class);

        // 3. JSONB
        MetadataStorageConfig jsonbConfig = DefaultMetadataStorageConfig.combinedJsonb();
        MetadataHandler jsonbHandler = MetadataHandlerFactory.create(jsonbConfig);
        assertThat(jsonbHandler).isInstanceOf(JsonbMetadataHandler.class);
    }
}
