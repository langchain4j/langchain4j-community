package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Configuration-based integration tests for YugabyteDBEmbeddingStore.
 * Tests all three metadata storage modes and SQL injection prevention.
 *
 * Uses TestContainers for isolated YugabyteDB instance
 */
@Testcontainers
abstract class YugabyteDBEmbeddingStoreConfigIT extends EmbeddingStoreWithFilteringIT {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBEmbeddingStoreConfigIT.class);

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> yugabyteContainer = new GenericContainer<>(
                    DockerImageName.parse("yugabytedb/yugabyte:2025.1.0.1-b3"))
            .withExposedPorts(5433, 7000, 9000, 15433, 9042)
            .withCommand("bin/yugabyted", "start", "--background=false")
            .waitingFor(Wait.forListeningPorts(5433).withStartupTimeout(Duration.ofMinutes(5)));

    static YugabyteDBEmbeddingStore embeddingStore;
    static YugabyteDBEngine engine;
    static HikariDataSource dataSource;
    static EmbeddingModel embeddingModel;

    static String TABLE_NAME = "test_config"; // Will be overridden by each test class
    static final int TABLE_DIMENSION = 384;

    // TestContainers connection details
    private static final String DB_NAME = "yugabyte";
    private static final String DB_USER = "yugabyte";
    private static final String DB_PASSWORD = "yugabyte";

    @BeforeAll
    static void setup() {
        logger.info("🚀 [SETUP] Initializing YugabyteDB Testcontainer setup...");

        logger.info("🧠 [SETUP] Creating embedding model (AllMiniLmL6V2QuantizedEmbeddingModel)...");
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        logger.info("✅ [SETUP] Embedding model created successfully");

        logger.info("📋 [SETUP] Container details:");
        logger.info("[SETUP]   - Image: yugabytedb/yugabyte:2025.1.0.1-b3");
        logger.info("[SETUP]   - Host: {}", yugabyteContainer.getHost());
        logger.info("[SETUP]   - Mapped port: {}", yugabyteContainer.getMappedPort(5433));
        logger.info("[SETUP]   - Container ID: {}", yugabyteContainer.getContainerId());

        // Wait for YugabyteDB to be ready and enable pgvector extension
        logger.info("⏳ [SETUP] Waiting 10 seconds for YugabyteDB to fully initialize...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("🔧 [SETUP] Enabling pgvector extension in YugabyteDB...");
        try {
            yugabyteContainer.execInContainer(
                    "/home/yugabyte/bin/ysqlsh",
                    "-h",
                    "localhost",
                    "-U",
                    DB_USER,
                    "-d",
                    DB_NAME,
                    "-c",
                    "CREATE EXTENSION IF NOT EXISTS vector;");
            logger.info("✅ [SETUP] pgvector extension enabled successfully");
        } catch (Exception e) {
            logger.warn("⚠️ [SETUP] Warning: Failed to enable pgvector extension: {}", e.getMessage());
        }

        // Create PostgreSQL driver engine (recommended)
        logger.info("🔧 [SETUP] Creating YugabyteDBEngine with PostgreSQL driver...");
        logger.info("🔧 [SETUP] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");
        logger.info(
                "🔧 [SETUP] Connection URL: jdbc:postgresql://{}:{}/{}",
                yugabyteContainer.getHost(),
                yugabyteContainer.getMappedPort(5433),
                DB_NAME);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format(
                "jdbc:postgresql://%s:%d/%s",
                yugabyteContainer.getHost(), yugabyteContainer.getMappedPort(5433), DB_NAME));
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(config);
        engine = YugabyteDBEngine.from(dataSource);
        logger.info("✅ [SETUP] YugabyteDBEngine created successfully");

        logger.info("🎉 [SETUP] YugabyteDB Testcontainer setup completed successfully");
    }

    /**
     * Configures the embedding store with the specified metadata storage configuration.
     * This method creates the store instance with the specified configuration.
     */
    static void configureStore(String tableName, MetadataStorageConfig config) {
        logger.info(
                "🔧 [CONFIG] Configuring YugabyteDBEmbeddingStore with table: {} and config: {}",
                tableName,
                config.storageMode());

        // Set the table name for this test class
        TABLE_NAME = tableName;
        logger.info("📋 [CONFIG] Using table name: {}", TABLE_NAME);

        // Create embedding store with the specified configuration
        logger.info(
                "🏗️ [CONFIG] Creating YugabyteDBEmbeddingStore with metadata storage mode: {}", config.storageMode());
        embeddingStore = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName(TABLE_NAME)
                .dimension(TABLE_DIMENSION)
                .metricType(MetricType.COSINE)
                .createTableIfNotExists(true)
                .metadataStorageConfig(config)
                .build();
        logger.info("✅ [CONFIG] YugabyteDBEmbeddingStore configured successfully");
    }

    @AfterAll
    static void cleanup() {
        logger.info("🧹 [CLEANUP] Starting YugabyteDBEmbeddingStoreConfigIT cleanup...");

        if (engine != null) {
            logger.info("[CLEANUP] Closing YugabyteDBEngine...");
            engine.close();
        }
        if (dataSource != null) {
            logger.info("[CLEANUP] Closing HikariDataSource...");
            dataSource.close();
        }

        logger.info("✅ [CLEANUP] YugabyteDBEmbeddingStoreConfigIT cleanup completed");
        // Note: TestContainers will automatically clean up the container
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // Clear data without dropping/recreating table (much faster!)
        // This runs before each test from the parent class
        if (embeddingStore != null) {
            try {
                embeddingStore.removeAll();
                logger.debug("🧹 [TEST-SETUP] Cleared all data from table: {}", TABLE_NAME);
            } catch (Exception e) {
                logger.warn("⚠️ [TEST-SETUP] Failed to clear store: {}", e.getMessage());
                // If removeAll fails, table might not exist yet - create it
                embeddingStore.createTableIfNotExists();
            }
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    /**
     * Tests comprehensive SQL injection prevention across all metadata storage modes.
     * Ensures that malicious SQL in filter values cannot compromise the database.
     * Tests multiple attack vectors and verifies data integrity.
     */
    @Test
    void sqlInjectionShouldBePrevented() {
        // Setup test data with multiple embeddings and metadata
        List<TextSegment> testSegments = Arrays.asList(
                TextSegment.from(
                        "secure document 1", Metadata.from("category", "public").put("priority", 1)),
                TextSegment.from(
                        "secure document 2",
                        Metadata.from("category", "private").put("priority", 2)),
                TextSegment.from(
                        "secure document 3",
                        Metadata.from("category", "confidential").put("priority", 3)));

        List<Embedding> testEmbeddings = testSegments.stream()
                .map(segment -> embeddingModel().embed(segment.text()).content())
                .collect(java.util.stream.Collectors.toList());

        // Add all test documents
        embeddingStore().addAll(testEmbeddings, testSegments);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(3));

        // Test various SQL injection attack vectors
        List<String> maliciousInputs = Arrays.asList(
                // Classic SQL injection attempts
                "'; DROP TABLE " + TABLE_NAME + "; --",
                "' OR '1'='1",
                "'; DELETE FROM " + TABLE_NAME + "; --",
                "' UNION SELECT * FROM information_schema.tables; --",

                // Advanced injection attempts
                "'; INSERT INTO " + TABLE_NAME + " VALUES ('malicious'); --",
                "' OR 1=1; UPDATE " + TABLE_NAME + " SET content='hacked'; --",
                "'; CREATE TABLE hacked_table (id INT); --",

                // Encoded injection attempts
                "%27%3B%20DROP%20TABLE%20" + TABLE_NAME + "%3B%20--",
                "\\'; DROP TABLE " + TABLE_NAME + "; --");

        Embedding queryEmbedding = embeddingModel().embed("test query").content();

        // Test each malicious input
        for (String maliciousInput : maliciousInputs) {
            logger.info(
                    "🛡️ [SECURITY] Testing SQL injection prevention with: {}",
                    maliciousInput.length() > 50 ? maliciousInput.substring(0, 50) + "..." : maliciousInput);

            Filter maliciousFilter = metadataKey("category").isEqualTo(maliciousInput);

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(10)
                    .filter(maliciousFilter)
                    .build();

            try {
                // Execute search with malicious filter
                var results = embeddingStore().search(searchRequest);
                // Should return empty results (no matches) but not crash
                assertThat(results.matches()).isEmpty();
            } catch (Exception e) {
                // Some malicious inputs might cause exceptions, which is acceptable
                // as long as they don't compromise the database
                logger.info(
                        "🛡️ [SECURITY] Exception caught (expected): {}",
                        e.getClass().getSimpleName());
            }
        }

        // Verify database integrity - all original data should still be there
        awaitUntilAsserted(() -> {
            List<EmbeddingMatch<TextSegment>> allResults = getAllEmbeddings();
            assertThat(allResults).hasSize(3);

            // Verify original data is intact
            Set<String> originalTexts =
                    testSegments.stream().map(TextSegment::text).collect(java.util.stream.Collectors.toSet());

            Set<String> retrievedTexts = allResults.stream()
                    .map(match -> match.embedded().text())
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(retrievedTexts).containsExactlyInAnyOrderElementsOf(originalTexts);
        });

        // Test that legitimate queries still work after injection attempts
        Filter legitimateFilter = metadataKey("category").isEqualTo("public");
        EmbeddingSearchRequest legitimateRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .filter(legitimateFilter)
                .build();

        var legitimateResults = embeddingStore().search(legitimateRequest);
        assertThat(legitimateResults.matches()).hasSize(1);
        assertThat(legitimateResults.matches().get(0).embedded().text()).isEqualTo("secure document 1");

        System.out.println("✅ SQL injection prevention test completed successfully");
    }

    /**
     * Tests that the configured metadata storage mode works correctly.
     * This is an abstract method that each concrete test class should implement
     * to verify their specific storage mode behavior.
     */
    @Test
    abstract void testMetadataStorageMode();

    /**
     * Tests embedding search with metadata filtering for the configured storage mode.
     */
    @Test
    abstract void testSearchWithMetadataFilter();

    /**
     * Custom test for column-per-key metadata storage with proper await logic.
     * Tests that metadata is correctly stored and retrieved from individual columns.
     */
    @Test
    public void should_add_embedding_with_segment_with_column_metadata() {
        EmbeddingStore<TextSegment> store = embeddingStore();
        EmbeddingModel model = embeddingModel();

        TextSegment segment = TextSegment.from("test", Metadata.from("key", "value"));
        Embedding embedding = model.embed(segment.text()).content();

        store.add(embedding, segment);

        awaitUntilAsserted(() -> {
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(1)
                    .build();
            List<EmbeddingMatch<TextSegment>> relevant =
                    store.search(searchRequest).matches();
            assertThat(relevant).hasSize(1);
            assertThat(relevant.get(0).embedded().text()).isEqualTo("test");
            assertThat(relevant.get(0).embedded().metadata().getString("key")).isEqualTo("value");
        });
    }

    /**
     * Concrete test class for COLUMN_PER_KEY metadata storage mode.
     */
    static class ColumnPerKeyConfigIT extends YugabyteDBEmbeddingStoreConfigIT {

        // Listing all the columns are important to ensure that the test is comprehensive
        // and that all the columns are supported by the metadata storage mode
        static {
            MetadataStorageConfig config = DefaultMetadataStorageConfig.columnPerKey(Arrays.asList(
                    // Original test columns
                    "user_id UUID",
                    "category TEXT",
                    "priority INTEGER",
                    "age DOUBLE PRECISION",
                    "key TEXT",
                    "name TEXT",
                    "city TEXT",
                    "country TEXT",
                    "found_in TEXT",
                    // Parent class test columns
                    "integer_max INTEGER",
                    "integer_min INTEGER",
                    "integer_0 INTEGER",
                    "integer_1 INTEGER",
                    "integer_minus_1 INTEGER",
                    "float_max REAL",
                    "float_min REAL",
                    "float_0 REAL",
                    "float_1 REAL",
                    "float_minus_1 REAL",
                    "float_123 REAL",
                    "double_0 DOUBLE PRECISION",
                    "double_1 DOUBLE PRECISION",
                    "double_minus_1 DOUBLE PRECISION",
                    "double_123 DOUBLE PRECISION",
                    "long_max BIGINT",
                    "long_min BIGINT",
                    "long_0 BIGINT",
                    "long_1 BIGINT",
                    "long_minus_1 BIGINT",
                    "long_1746714878034235396 BIGINT",
                    "string_abc TEXT",
                    "string_empty TEXT",
                    "string_space TEXT",
                    "uuid UUID"));
            configureStore("test_config_column", config);
        }

        @Override
        @Test
        void testMetadataStorageMode() {
            // Test that column-based metadata works
            Metadata metadata = new Metadata();
            metadata.put("user_id", "123e4567-e89b-12d3-a456-426614174000");
            metadata.put("category", "important");
            metadata.put("priority", 1);
            TextSegment segment = TextSegment.from("test content", metadata);

            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddingStore().add(embedding, segment);

            awaitUntilAsserted(() -> {
                var results = embeddingStore()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build());
                assertThat(results.matches()).hasSize(1);
                assertThat(results.matches().get(0).embedded().metadata().getString("category"))
                        .isEqualTo("important");
            });
        }

        @Override
        @Test
        void testSearchWithMetadataFilter() {
            // Add embeddings with different metadata
            Metadata metadata1 = new Metadata();
            metadata1.put("category", "work");
            metadata1.put("priority", 1);
            TextSegment segment1 = TextSegment.from("content 1", metadata1);

            Metadata metadata2 = new Metadata();
            metadata2.put("category", "personal");
            metadata2.put("priority", 2);
            TextSegment segment2 = TextSegment.from("content 2", metadata2);

            Embedding embedding1 = embeddingModel().embed(segment1.text()).content();
            Embedding embedding2 = embeddingModel().embed(segment2.text()).content();

            embeddingStore().add(embedding1, segment1);
            embeddingStore().add(embedding2, segment2);

            // Search with category filter
            Filter filter = metadataKey("category").isEqualTo("work");
            var results = embeddingStore()
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding1)
                            .maxResults(10)
                            .filter(filter)
                            .build());

            assertThat(results.matches()).hasSize(1);
            assertThat(results.matches().get(0).embedded().metadata().getString("category"))
                    .isEqualTo("work");
        }
    }

    /**
     * Concrete test class for COMBINED_JSON metadata storage mode.
     */
    static class CombinedJsonConfigIT extends YugabyteDBEmbeddingStoreConfigIT {

        static {
            MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJson();
            configureStore("test_config_json", config);
        }

        @Override
        @Test
        void testMetadataStorageMode() {
            // Test that JSON metadata works
            Metadata metadata = new Metadata();
            metadata.put("user_id", "user123");
            metadata.put("tags", Arrays.asList("tag1", "tag2").toString());
            metadata.put("score", 95.5);
            TextSegment segment = TextSegment.from("test content", metadata);

            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddingStore().add(embedding, segment);

            awaitUntilAsserted(() -> {
                var results = embeddingStore()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build());
                assertThat(results.matches()).hasSize(1);
                assertThat(results.matches().get(0).embedded().metadata().getString("user_id"))
                        .isEqualTo("user123");
            });
        }

        @Override
        @Test
        void testSearchWithMetadataFilter() {
            // Add embeddings with different metadata
            Metadata metadata1 = new Metadata();
            metadata1.put("type", "document");
            metadata1.put("status", "published");
            TextSegment segment1 = TextSegment.from("content 1", metadata1);

            Metadata metadata2 = new Metadata();
            metadata2.put("type", "image");
            metadata2.put("status", "draft");
            TextSegment segment2 = TextSegment.from("content 2", metadata2);

            Embedding embedding1 = embeddingModel().embed(segment1.text()).content();
            Embedding embedding2 = embeddingModel().embed(segment2.text()).content();

            embeddingStore().add(embedding1, segment1);
            embeddingStore().add(embedding2, segment2);

            // Search with type filter
            Filter filter = metadataKey("type").isEqualTo("document");
            var results = embeddingStore()
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding1)
                            .maxResults(10)
                            .filter(filter)
                            .build());

            assertThat(results.matches()).hasSize(1);
            assertThat(results.matches().get(0).embedded().metadata().getString("type"))
                    .isEqualTo("document");
        }
    }

    /**
     * Concrete test class for COMBINED_JSONB metadata storage mode.
     */
    static class CombinedJsonbConfigIT extends YugabyteDBEmbeddingStoreConfigIT {

        static {
            MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJsonb();
            configureStore("test_config_jsonb", config);
        }

        @Override
        @Test
        void testMetadataStorageMode() {
            // Test that JSONB metadata works with complex data
            Metadata metadata = new Metadata();
            metadata.put("user_id", "user123");
            metadata.put("nested", "complex_value");
            metadata.put("number", 42);
            metadata.put("boolean", "true");
            TextSegment segment = TextSegment.from("test content", metadata);

            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddingStore().add(embedding, segment);

            awaitUntilAsserted(() -> {
                var results = embeddingStore()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build());
                assertThat(results.matches()).hasSize(1);
                assertThat(results.matches().get(0).embedded().metadata().getString("user_id"))
                        .isEqualTo("user123");
                assertThat(results.matches().get(0).embedded().metadata().getInteger("number"))
                        .isEqualTo(42);
            });
        }

        @Override
        @Test
        void testSearchWithMetadataFilter() {
            // Add embeddings with different metadata
            Metadata metadata1 = new Metadata();
            metadata1.put("department", "engineering");
            metadata1.put("level", "senior");
            TextSegment segment1 = TextSegment.from("content 1", metadata1);

            Metadata metadata2 = new Metadata();
            metadata2.put("department", "marketing");
            metadata2.put("level", "junior");
            TextSegment segment2 = TextSegment.from("content 2", metadata2);

            Embedding embedding1 = embeddingModel().embed(segment1.text()).content();
            Embedding embedding2 = embeddingModel().embed(segment2.text()).content();

            embeddingStore().add(embedding1, segment1);
            embeddingStore().add(embedding2, segment2);

            // Search with department filter
            Filter filter = metadataKey("department").isEqualTo("engineering");
            var results = embeddingStore()
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding1)
                            .maxResults(10)
                            .filter(filter)
                            .build());

            assertThat(results.matches()).hasSize(1);
            assertThat(results.matches().get(0).embedded().metadata().getString("department"))
                    .isEqualTo("engineering");
        }
    }

    /**
     * Test configuration with YugabyteDB Smart Driver
     */
    static class SmartDriverConfigIT extends YugabyteDBEmbeddingStoreConfigIT {

        @BeforeAll
        static void setupSmartDriver() {
            logger.info("🚀 [SMART-DRIVER-SETUP] Initializing YugabyteDB Smart Driver setup...");
            logger.info(
                    "🔧 [SMART-DRIVER-SETUP] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            logger.info("🧠 [SMART-DRIVER-SETUP] Creating embedding model (AllMiniLmL6V2QuantizedEmbeddingModel)...");
            embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
            logger.info("✅ [SMART-DRIVER-SETUP] Embedding model created successfully");

            logger.info("📋 [SMART-DRIVER-SETUP] Container details:");
            logger.info("[SMART-DRIVER-SETUP]   - Image: yugabytedb/yugabyte:2025.1.0.1-b3");
            logger.info("[SMART-DRIVER-SETUP]   - Host: {}", yugabyteContainer.getHost());
            logger.info("[SMART-DRIVER-SETUP]   - Mapped port: {}", yugabyteContainer.getMappedPort(5433));
            logger.info("[SMART-DRIVER-SETUP]   - Container ID: {}", yugabyteContainer.getContainerId());

            // Wait for YugabyteDB to be ready and enable pgvector extension
            logger.info("⏳ [SMART-DRIVER-SETUP] Waiting 10 seconds for YugabyteDB to fully initialize...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("🔧 [SMART-DRIVER-SETUP] Enabling pgvector extension in YugabyteDB...");
            try {
                yugabyteContainer.execInContainer(
                        "/home/yugabyte/bin/ysqlsh",
                        "-h",
                        "localhost",
                        "-U",
                        DB_USER,
                        "-d",
                        DB_NAME,
                        "-c",
                        "CREATE EXTENSION IF NOT EXISTS vector;");
                logger.info("✅ [SMART-DRIVER-SETUP] pgvector extension enabled successfully");
            } catch (Exception e) {
                logger.warn("⚠️ [SMART-DRIVER-SETUP] Warning: Failed to enable pgvector extension: {}", e.getMessage());
            }

            // Create YugabyteDB Smart Driver engine
            logger.info("🔧 [SMART-DRIVER-SETUP] Creating YugabyteDB Smart Driver engine...");
            logger.info(
                    "🔧 [SMART-DRIVER-SETUP] Connection URL: jdbc:yugabytedb://{}:{}/{}",
                    yugabyteContainer.getHost(),
                    yugabyteContainer.getMappedPort(5433),
                    DB_NAME);

            try {
                engine = YugabyteDBEngine.builder()
                        .host(yugabyteContainer.getHost())
                        .port(yugabyteContainer.getMappedPort(5433))
                        .database(DB_NAME)
                        .username(DB_USER)
                        .password(DB_PASSWORD)
                        .usePostgreSQLDriver(false) // Use Smart Driver (default)
                        .maxPoolSize(10)
                        .build();
                logger.info("✅ [SMART-DRIVER-SETUP] YugabyteDB Smart Driver engine created successfully");
            } catch (Exception e) {
                logger.error("❌ [SMART-DRIVER-SETUP] Failed to create Smart Driver engine: {}", e.getMessage());
                throw new RuntimeException("Failed to setup Smart Driver", e);
            }

            // Configure store with Smart Driver engine
            MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJson();
            configureStore("test_config_smart", config);

            logger.info("🎉 [SMART-DRIVER-SETUP] YugabyteDB Smart Driver setup completed successfully");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            logger.info("🧹 [SMART-DRIVER-CLEANUP] Starting Smart Driver cleanup...");

            if (engine != null) {
                logger.info("[SMART-DRIVER-CLEANUP] Closing YugabyteDB Smart Driver engine...");
                engine.close();
            }

            logger.info("✅ [SMART-DRIVER-CLEANUP] Smart Driver cleanup completed");
        }

        @Override
        @Test
        void testMetadataStorageMode() {
            logger.info("🧪 [SMART-DRIVER] Testing metadata storage mode with Smart Driver...");
            logger.info(
                    "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            // Test that JSON metadata works with Smart Driver
            Metadata metadata = new Metadata();
            metadata.put("user_id", "smart_user123");
            metadata.put("tags", Arrays.asList("smart", "driver").toString());
            metadata.put("score", 99.9);
            TextSegment segment = TextSegment.from("Smart Driver test content", metadata);

            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddingStore().add(embedding, segment);

            awaitUntilAsserted(() -> {
                var results = embeddingStore()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build());
                assertThat(results.matches()).hasSize(1);
                assertThat(results.matches().get(0).embedded().metadata().getString("user_id"))
                        .isEqualTo("smart_user123");
            });

            logger.info("✅ [SMART-DRIVER] Metadata storage mode test completed successfully");
        }

        @Override
        @Test
        void testSearchWithMetadataFilter() {
            logger.info("🧪 [SMART-DRIVER] Testing search with metadata filter using Smart Driver...");
            logger.info(
                    "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            // Add embeddings with different metadata
            Metadata metadata1 = new Metadata();
            metadata1.put("type", "smart_document");
            metadata1.put("status", "active");
            TextSegment segment1 = TextSegment.from("Smart Driver content 1", metadata1);

            Metadata metadata2 = new Metadata();
            metadata2.put("type", "smart_image");
            metadata2.put("status", "inactive");
            TextSegment segment2 = TextSegment.from("Smart Driver content 2", metadata2);

            Embedding embedding1 = embeddingModel().embed(segment1.text()).content();
            Embedding embedding2 = embeddingModel().embed(segment2.text()).content();

            embeddingStore().add(embedding1, segment1);
            embeddingStore().add(embedding2, segment2);

            // Search with type filter
            Filter filter = metadataKey("type").isEqualTo("smart_document");
            var results = embeddingStore()
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding1)
                            .maxResults(10)
                            .filter(filter)
                            .build());

            assertThat(results.matches()).hasSize(1);
            assertThat(results.matches().get(0).embedded().metadata().getString("type"))
                    .isEqualTo("smart_document");

            logger.info("✅ [SMART-DRIVER] Search with metadata filter test completed successfully");
        }
    }

    /**
     * Test COLUMN_PER_KEY metadata storage mode with YugabyteDB Smart Driver.
     * Tests that individual metadata columns work correctly with Smart Driver.
     */
    static class SmartDriverColumnPerKeyConfigIT extends YugabyteDBEmbeddingStoreConfigIT {

        @BeforeAll
        static void setupSmartDriver() {
            logger.info("🚀 [SMART-DRIVER-COLUMN] Initializing Smart Driver with COLUMN_PER_KEY config...");
            logger.info(
                    "🔧 [SMART-DRIVER-COLUMN] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            logger.info("🧠 [SMART-DRIVER-COLUMN] Creating embedding model...");
            embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
            logger.info("✅ [SMART-DRIVER-COLUMN] Embedding model created successfully");

            logger.info("📋 [SMART-DRIVER-COLUMN] Container details:");
            logger.info("[SMART-DRIVER-COLUMN]   - Host: {}", yugabyteContainer.getHost());
            logger.info("[SMART-DRIVER-COLUMN]   - Port: {}", yugabyteContainer.getMappedPort(5433));

            // Wait for YugabyteDB to be ready
            logger.info("⏳ [SMART-DRIVER-COLUMN] Waiting for YugabyteDB initialization...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("🔧 [SMART-DRIVER-COLUMN] Enabling pgvector extension...");
            try {
                yugabyteContainer.execInContainer(
                        "/home/yugabyte/bin/ysqlsh",
                        "-h",
                        "localhost",
                        "-U",
                        DB_USER,
                        "-d",
                        DB_NAME,
                        "-c",
                        "CREATE EXTENSION IF NOT EXISTS vector;");
                logger.info("✅ [SMART-DRIVER-COLUMN] pgvector extension enabled successfully");
            } catch (Exception e) {
                logger.warn("⚠️ [SMART-DRIVER-COLUMN] Warning: Failed to enable pgvector: {}", e.getMessage());
            }

            // Create YugabyteDB Smart Driver engine
            logger.info("🔧 [SMART-DRIVER-COLUMN] Creating Smart Driver engine...");
            try {
                engine = YugabyteDBEngine.builder()
                        .host(yugabyteContainer.getHost())
                        .port(yugabyteContainer.getMappedPort(5433))
                        .database(DB_NAME)
                        .username(DB_USER)
                        .password(DB_PASSWORD)
                        .usePostgreSQLDriver(false) // Use Smart Driver
                        .maxPoolSize(10)
                        .build();
                logger.info("✅ [SMART-DRIVER-COLUMN] Smart Driver engine created successfully");
            } catch (Exception e) {
                logger.error("❌ [SMART-DRIVER-COLUMN] Failed to create engine: {}", e.getMessage());
                throw new RuntimeException("Failed to setup Smart Driver", e);
            }

            // Configure store with COLUMN_PER_KEY metadata storage
            MetadataStorageConfig config = DefaultMetadataStorageConfig.columnPerKey(Arrays.asList(
                    "user_id UUID",
                    "category TEXT",
                    "priority INTEGER",
                    "age DOUBLE PRECISION",
                    "key TEXT",
                    "name TEXT",
                    "city TEXT",
                    "country TEXT",
                    "found_in TEXT",
                    "integer_max INTEGER",
                    "integer_min INTEGER",
                    "integer_0 INTEGER",
                    "integer_1 INTEGER",
                    "integer_minus_1 INTEGER",
                    "float_max REAL",
                    "float_min REAL",
                    "float_0 REAL",
                    "float_1 REAL",
                    "float_minus_1 REAL",
                    "float_123 REAL",
                    "double_0 DOUBLE PRECISION",
                    "double_1 DOUBLE PRECISION",
                    "double_minus_1 DOUBLE PRECISION",
                    "double_123 DOUBLE PRECISION",
                    "long_max BIGINT",
                    "long_min BIGINT",
                    "long_0 BIGINT",
                    "long_1 BIGINT",
                    "long_minus_1 BIGINT",
                    "long_1746714878034235396 BIGINT",
                    "string_abc TEXT",
                    "string_empty TEXT",
                    "string_space TEXT",
                    "uuid UUID"));
            configureStore("test_config_smart_column", config);

            logger.info("🎉 [SMART-DRIVER-COLUMN] Smart Driver COLUMN_PER_KEY setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            logger.info("🧹 [SMART-DRIVER-COLUMN] Starting cleanup...");

            if (engine != null) {
                logger.info("[SMART-DRIVER-COLUMN] Closing engine...");
                engine.close();
            }

            logger.info("✅ [SMART-DRIVER-COLUMN] Cleanup completed");
        }

        @Override
        @Test
        void testMetadataStorageMode() {
            logger.info("🧪 [SMART-DRIVER-COLUMN] Testing COLUMN_PER_KEY metadata storage...");

            // Test that column-based metadata works with Smart Driver
            Metadata metadata = new Metadata();
            metadata.put("user_id", "123e4567-e89b-12d3-a456-426614174000");
            metadata.put("category", "smart_important");
            metadata.put("priority", 1);
            TextSegment segment = TextSegment.from("Smart Driver column test", metadata);

            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddingStore().add(embedding, segment);

            awaitUntilAsserted(() -> {
                var results = embeddingStore()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build());
                assertThat(results.matches()).hasSize(1);
                assertThat(results.matches().get(0).embedded().metadata().getString("category"))
                        .isEqualTo("smart_important");
            });

            logger.info("✅ [SMART-DRIVER-COLUMN] COLUMN_PER_KEY test completed successfully");
        }

        @Override
        @Test
        void testSearchWithMetadataFilter() {
            logger.info("🧪 [SMART-DRIVER-COLUMN] Testing metadata filter with COLUMN_PER_KEY...");

            // Add embeddings with different metadata
            Metadata metadata1 = new Metadata();
            metadata1.put("category", "smart_work");
            metadata1.put("priority", 1);
            TextSegment segment1 = TextSegment.from("Smart Driver work content", metadata1);

            Metadata metadata2 = new Metadata();
            metadata2.put("category", "smart_personal");
            metadata2.put("priority", 2);
            TextSegment segment2 = TextSegment.from("Smart Driver personal content", metadata2);

            Embedding embedding1 = embeddingModel().embed(segment1.text()).content();
            Embedding embedding2 = embeddingModel().embed(segment2.text()).content();

            embeddingStore().add(embedding1, segment1);
            embeddingStore().add(embedding2, segment2);

            // Search with category filter
            Filter filter = metadataKey("category").isEqualTo("smart_work");
            var results = embeddingStore()
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding1)
                            .maxResults(10)
                            .filter(filter)
                            .build());

            assertThat(results.matches()).hasSize(1);
            assertThat(results.matches().get(0).embedded().metadata().getString("category"))
                    .isEqualTo("smart_work");

            logger.info("✅ [SMART-DRIVER-COLUMN] Metadata filter test completed successfully");
        }
    }

    /**
     * Test COMBINED_JSONB metadata storage mode with YugabyteDB Smart Driver.
     * Tests that JSONB metadata works correctly with Smart Driver.
     */
    static class SmartDriverCombinedJsonbConfigIT extends YugabyteDBEmbeddingStoreConfigIT {

        @BeforeAll
        static void setupSmartDriver() {
            logger.info("🚀 [SMART-DRIVER-JSONB] Initializing Smart Driver with COMBINED_JSONB config...");
            logger.info(
                    "🔧 [SMART-DRIVER-JSONB] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            logger.info("🧠 [SMART-DRIVER-JSONB] Creating embedding model...");
            embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
            logger.info("✅ [SMART-DRIVER-JSONB] Embedding model created successfully");

            logger.info("📋 [SMART-DRIVER-JSONB] Container details:");
            logger.info("[SMART-DRIVER-JSONB]   - Host: {}", yugabyteContainer.getHost());
            logger.info("[SMART-DRIVER-JSONB]   - Port: {}", yugabyteContainer.getMappedPort(5433));

            // Wait for YugabyteDB to be ready
            logger.info("⏳ [SMART-DRIVER-JSONB] Waiting for YugabyteDB initialization...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("🔧 [SMART-DRIVER-JSONB] Enabling pgvector extension...");
            try {
                yugabyteContainer.execInContainer(
                        "/home/yugabyte/bin/ysqlsh",
                        "-h",
                        "localhost",
                        "-U",
                        DB_USER,
                        "-d",
                        DB_NAME,
                        "-c",
                        "CREATE EXTENSION IF NOT EXISTS vector;");
                logger.info("✅ [SMART-DRIVER-JSONB] pgvector extension enabled successfully");
            } catch (Exception e) {
                logger.warn("⚠️ [SMART-DRIVER-JSONB] Warning: Failed to enable pgvector: {}", e.getMessage());
            }

            // Create YugabyteDB Smart Driver engine
            logger.info("🔧 [SMART-DRIVER-JSONB] Creating Smart Driver engine...");
            try {
                engine = YugabyteDBEngine.builder()
                        .host(yugabyteContainer.getHost())
                        .port(yugabyteContainer.getMappedPort(5433))
                        .database(DB_NAME)
                        .username(DB_USER)
                        .password(DB_PASSWORD)
                        .usePostgreSQLDriver(false) // Use Smart Driver
                        .maxPoolSize(10)
                        .build();
                logger.info("✅ [SMART-DRIVER-JSONB] Smart Driver engine created successfully");
            } catch (Exception e) {
                logger.error("❌ [SMART-DRIVER-JSONB] Failed to create engine: {}", e.getMessage());
                throw new RuntimeException("Failed to setup Smart Driver", e);
            }

            // Configure store with COMBINED_JSONB metadata storage
            MetadataStorageConfig config = DefaultMetadataStorageConfig.combinedJsonb();
            configureStore("test_config_smart_jsonb", config);

            logger.info("🎉 [SMART-DRIVER-JSONB] Smart Driver COMBINED_JSONB setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            logger.info("🧹 [SMART-DRIVER-JSONB] Starting cleanup...");

            if (engine != null) {
                logger.info("[SMART-DRIVER-JSONB] Closing engine...");
                engine.close();
            }

            logger.info("✅ [SMART-DRIVER-JSONB] Cleanup completed");
        }

        @Override
        @Test
        void testMetadataStorageMode() {
            logger.info("🧪 [SMART-DRIVER-JSONB] Testing COMBINED_JSONB metadata storage...");

            // Test that JSONB metadata works with complex data
            Metadata metadata = new Metadata();
            metadata.put("user_id", "smart_user123");
            metadata.put("nested", "smart_complex_value");
            metadata.put("number", 42);
            metadata.put("boolean", "true");
            TextSegment segment = TextSegment.from("Smart Driver JSONB test", metadata);

            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddingStore().add(embedding, segment);

            awaitUntilAsserted(() -> {
                var results = embeddingStore()
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build());
                assertThat(results.matches()).hasSize(1);
                assertThat(results.matches().get(0).embedded().metadata().getString("user_id"))
                        .isEqualTo("smart_user123");
                assertThat(results.matches().get(0).embedded().metadata().getInteger("number"))
                        .isEqualTo(42);
            });

            logger.info("✅ [SMART-DRIVER-JSONB] COMBINED_JSONB test completed successfully");
        }

        @Override
        @Test
        void testSearchWithMetadataFilter() {
            logger.info("🧪 [SMART-DRIVER-JSONB] Testing metadata filter with COMBINED_JSONB...");

            // Add embeddings with different metadata
            Metadata metadata1 = new Metadata();
            metadata1.put("department", "smart_engineering");
            metadata1.put("level", "senior");
            TextSegment segment1 = TextSegment.from("Smart Driver engineering content", metadata1);

            Metadata metadata2 = new Metadata();
            metadata2.put("department", "smart_marketing");
            metadata2.put("level", "junior");
            TextSegment segment2 = TextSegment.from("Smart Driver marketing content", metadata2);

            Embedding embedding1 = embeddingModel().embed(segment1.text()).content();
            Embedding embedding2 = embeddingModel().embed(segment2.text()).content();

            embeddingStore().add(embedding1, segment1);
            embeddingStore().add(embedding2, segment2);

            // Search with department filter
            Filter filter = metadataKey("department").isEqualTo("smart_engineering");
            var results = embeddingStore()
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding1)
                            .maxResults(10)
                            .filter(filter)
                            .build());

            assertThat(results.matches()).hasSize(1);
            assertThat(results.matches().get(0).embedded().metadata().getString("department"))
                    .isEqualTo("smart_engineering");

            logger.info("✅ [SMART-DRIVER-JSONB] Metadata filter test completed successfully");
        }
    }
}
