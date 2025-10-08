package dev.langchain4j.community.store.embedding.yugabytedb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Test to verify MetadataStorageConfig implementations work correctly,
 * especially the JSON index validation fix.
 */
class MetadataStorageConfigTest {

    @Test
    void testDefaultConfig() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.defaultConfig();

        assertEquals(MetadataStorageMode.COMBINED_JSONB, config.storageMode());
        assertEquals(Collections.singletonList("metadata JSONB"), config.columnDefinitions());
        assertEquals(Collections.singletonList("metadata"), config.indexes());
        assertEquals("GIN", config.indexType());
    }

    @Test
    void testCombinedJsonbConfig() {
        MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJsonb();

        assertEquals(MetadataStorageMode.COMBINED_JSONB, config.storageMode());
        assertEquals(Collections.singletonList("metadata JSONB"), config.columnDefinitions());
        assertEquals(Collections.singletonList("metadata"), config.indexes());
        assertEquals("GIN", config.indexType());
    }

    @Test
    void testCombinedJsonConfig_NoIndexes() {
        // This should NOT throw an error anymore (fixed)
        MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJson();

        assertEquals(MetadataStorageMode.COMBINED_JSON, config.storageMode());
        assertEquals(Collections.singletonList("metadata JSON"), config.columnDefinitions());
        assertEquals(Collections.emptyList(), config.indexes()); // ✅ Fixed: No indexes
        assertNull(config.indexType()); // ✅ Fixed: No index type
    }

    @Test
    void testColumnPerKeyConfig() {
        MetadataStorageConfig config =
                DefaultMetadataStorageConfig.columnPerKey(Arrays.asList("user_id UUID", "category TEXT"));

        assertEquals(MetadataStorageMode.COLUMN_PER_KEY, config.storageMode());
        assertEquals(Arrays.asList("user_id UUID", "category TEXT"), config.columnDefinitions());
        assertEquals("BTREE", config.indexType());
    }

    @Test
    void testJsonHandlerCreation_NoError() {
        // This should work now (after the fix)
        MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJson();

        // Should not throw an exception
        assertDoesNotThrow(() -> {
            MetadataHandler handler = MetadataHandlerFactory.create(config);
            assertNotNull(handler);
            assertTrue(handler instanceof JsonMetadataHandler);
        });
    }

    @Test
    void testJsonHandlerWithManualIndexes_ShouldFail() {
        // This should still fail if someone manually adds indexes to JSON config
        MetadataStorageConfig config = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList("metadata JSON"))
                .indexes(Collections.singletonList("metadata")) // This should cause failure
                .build();

        // Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            MetadataHandlerFactory.create(config);
        });
    }

    @Test
    void testAllThreeHandlerTypes() {
        // Test that all three storage modes work

        // 1. Column-based
        MetadataStorageConfig columnConfig =
                DefaultMetadataStorageConfig.columnPerKey(Arrays.asList("user_id UUID", "category TEXT"));
        MetadataHandler columnHandler = MetadataHandlerFactory.create(columnConfig);
        assertTrue(columnHandler instanceof ColumnMetadataHandler);

        // 2. JSON (fixed)
        MetadataStorageConfig jsonConfig = DefaultMetadataStorageConfig.combinedJson();
        MetadataHandler jsonHandler = MetadataHandlerFactory.create(jsonConfig);
        assertTrue(jsonHandler instanceof JsonMetadataHandler);

        // 3. JSONB
        MetadataStorageConfig jsonbConfig = DefaultMetadataStorageConfig.combinedJsonb();
        MetadataHandler jsonbHandler = MetadataHandlerFactory.create(jsonbConfig);
        assertTrue(jsonbHandler instanceof JsonbMetadataHandler);
    }
}
