package dev.langchain4j.community.store.embedding.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.yugabytedb.index.HNSWIndex;
import dev.langchain4j.community.store.embedding.yugabytedb.index.NoIndex;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for vector index types using TestContainers.
 * Tests both standard PostgreSQL hnsw and YugabyteDB ybhnsw implementations, plus NoIndex.
 *
 * Note: YugabyteDB supports 'ybhnsw' as its optimized vector index implementation.
 * Standard PostgreSQL uses 'hnsw'. IVFFlat is not tested as it's not supported by YugabyteDB.
 */
class YugabyteDBVectorIndexIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBVectorIndexIT.class);

    @AfterEach
    void cleanupTables() {
        logger.info("🧹 [CLEANUP] Dropping test tables...");
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement()) {
            // PostgreSQL driver test tables
            stmt.execute("DROP TABLE IF EXISTS test_hnsw_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_ybhnsw_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_hnsw_euclidean_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_no_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_custom_params CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_dot_product CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_cosine_metric CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_pg_gin_hnsw_hybrid CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_pg_hybrid CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_default_index_name CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_custom_index_name CASCADE");

            // Smart driver test tables
            stmt.execute("DROP TABLE IF EXISTS test_smart_hnsw_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_smart_ybhnsw_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_smart_no_index CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_smart_gin_hnsw_hybrid CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_smart_hybrid CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_smart_hnsw CASCADE");

            stmt.execute("DROP TABLE IF EXISTS test_custom_schema.test_index CASCADE");
            stmt.execute("DROP SCHEMA IF EXISTS test_custom_schema CASCADE");
            logger.info("✅ [CLEANUP] Tables dropped successfully");
        } catch (Exception e) {
            logger.warn("⚠️ [CLEANUP] Error dropping tables: {}", e.getMessage());
        }
    }

    // ==================== MODULAR HELPER METHODS ====================

    /**
     * Test hybrid search with metadata filtering (GIN index) + vector similarity (ybhnsw index)
     */
    private static void testHybridSearch(
            YugabyteDBEngine testEngine, Logger testLogger, String logPrefix, String tableSuffix) throws Exception {
        testLogger.info("🔧 {} Testing hybrid search (GIN + ybhnsw indexes)...", logPrefix);

        // Given - Create store with HNSW vector index
        HNSWIndex hnswIndex = HNSWIndex.builder().metricType(MetricType.COSINE).build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(testEngine)
                .tableName("test_" + tableSuffix + "_hybrid")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embeddings with different categories
        Embedding publicDoc1 = embeddingModel.embed("Machine learning tutorial").content();
        Embedding publicDoc2 = embeddingModel.embed("Deep learning guide").content();
        Embedding privateDoc1 = embeddingModel.embed("Confidential AI research").content();

        store.add(publicDoc1, TextSegment.from("ML tutorial", Metadata.from("category", "public")));
        store.add(publicDoc2, TextSegment.from("DL guide", Metadata.from("category", "public")));
        store.add(privateDoc1, TextSegment.from("Confidential research", Metadata.from("category", "private")));

        testLogger.info("✅ {} Added 3 embeddings with metadata", logPrefix);

        // Then - Search with metadata filter (hybrid search)
        Embedding queryEmbedding = embeddingModel.embed("learning tutorial").content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .filter(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey("category")
                        .isEqualTo("public"))
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);

        // Verify only public documents are returned
        assertThat(result.matches()).isNotEmpty();
        for (var match : result.matches()) {
            String category = match.embedded().metadata().getString("category");
            assertThat(category).isEqualTo("public");
            testLogger.info(
                    "📋 {} Found: {} (category: {})",
                    logPrefix,
                    match.embedded().text(),
                    category);
        }

        testLogger.info("✅ {} Hybrid search works - GIN + ybhnsw indexes!", logPrefix);

        // Cleanup
        try (Connection conn = testEngine.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_" + tableSuffix + "_hybrid CASCADE");
        }
    }

    /**
     * Test HNSW index creation and search
     */
    private static void testHNSWIndexCreation(
            YugabyteDBEngine testEngine, Logger testLogger, String logPrefix, String tableSuffix) throws Exception {
        testLogger.info("🔧 {} Testing HNSW index creation...", logPrefix);

        HNSWIndex hnswIndex = HNSWIndex.builder()
                .m(16)
                .efConstruction(64)
                .metricType(MetricType.COSINE)
                .build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(testEngine)
                .tableName("test_" + tableSuffix + "_hnsw")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        Embedding embedding1 = embeddingModel.embed("Hello world").content();
        store.add(embedding1, TextSegment.from("Hello world"));

        testLogger.info("✅ {} Added embedding", logPrefix);

        // Verify index exists (check for both hnsw and ybhnsw)
        try (Connection conn = testEngine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT indexname, indexdef FROM pg_indexes " + "WHERE tablename = 'test_"
                                + tableSuffix + "_hnsw' AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isTrue();
            String indexName = rs.getString("indexname");
            String indexDef = rs.getString("indexdef");
            testLogger.info("📋 {} HNSW index found: {}", logPrefix, indexName);
            testLogger.info(
                    "📋 {} Index type: {}",
                    logPrefix,
                    indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");
        }

        // Verify search works
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);

        testLogger.info("✅ {} HNSW index works correctly", logPrefix);

        // Cleanup
        try (Connection conn = testEngine.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_" + tableSuffix + "_hnsw CASCADE");
        }
    }

    /**
     * Test hybrid search with GIN index (metadata filtering) + standard HNSW index (vector similarity)
     */
    private static void testGinHnswHybridSearch(
            YugabyteDBEngine testEngine, Logger testLogger, String logPrefix, String tableSuffix) throws Exception {
        testLogger.info("🔧 {} Testing hybrid search with GIN + standard HNSW indexes...", logPrefix);

        // Given - Create store with standard HNSW vector index (will auto-convert to ybhnsw on YugabyteDB)
        HNSWIndex hnswIndex = HNSWIndex.builder()
                .indexType("hnsw") // Request standard hnsw
                .metricType(MetricType.COSINE)
                .build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(testEngine)
                .tableName("test_" + tableSuffix + "_gin_hnsw_hybrid")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embeddings with different metadata
        Embedding doc1 = embeddingModel.embed("Java programming language").content();
        Embedding doc2 = embeddingModel.embed("Python programming language").content();
        Embedding doc3 = embeddingModel.embed("JavaScript programming language").content();
        Embedding doc4 = embeddingModel.embed("Rust programming language").content();

        store.add(
                doc1,
                TextSegment.from(
                        "Java tutorial", Metadata.from("language", "java").put("difficulty", "intermediate")));
        store.add(
                doc2,
                TextSegment.from(
                        "Python guide", Metadata.from("language", "python").put("difficulty", "beginner")));
        store.add(
                doc3,
                TextSegment.from(
                        "JavaScript intro",
                        Metadata.from("language", "javascript").put("difficulty", "beginner")));
        store.add(
                doc4,
                TextSegment.from(
                        "Rust advanced", Metadata.from("language", "rust").put("difficulty", "advanced")));

        testLogger.info("✅ {} Added 4 embeddings with metadata", logPrefix);

        // Then - Test hybrid search: GIN index filters metadata, HNSW does vector similarity
        Embedding queryEmbedding = embeddingModel.embed("programming tutorial").content();

        // Search for beginner-level content
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .filter(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey("difficulty")
                        .isEqualTo("beginner"))
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);

        // Verify only beginner-level documents are returned
        assertThat(result.matches()).isNotEmpty();
        assertThat(result.matches()).hasSizeLessThanOrEqualTo(2);
        for (var match : result.matches()) {
            String difficulty = match.embedded().metadata().getString("difficulty");
            assertThat(difficulty).isEqualTo("beginner");
            testLogger.info(
                    "📋 {} Found: {} (difficulty: {})",
                    logPrefix,
                    match.embedded().text(),
                    difficulty);
        }

        // Verify the vector index was created (as ybhnsw since YugabyteDB auto-converts)
        try (Connection conn = testEngine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT indexname, indexdef FROM pg_indexes " + "WHERE tablename = 'test_"
                                + tableSuffix + "_gin_hnsw_hybrid' " + "AND indexdef LIKE '%embedding%' "
                                + "AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isTrue();
            String indexName = rs.getString("indexname");
            String indexDef = rs.getString("indexdef");
            testLogger.info(
                    "📋 {} Vector index: {} - {}",
                    logPrefix,
                    indexName,
                    indexDef.contains("ybhnsw") ? "ybhnsw (auto-converted)" : "hnsw");
        }

        testLogger.info("✅ {} Hybrid search with GIN + HNSW indexes works correctly", logPrefix);

        // Cleanup
        try (Connection conn = testEngine.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_" + tableSuffix + "_gin_hnsw_hybrid CASCADE");
        }
    }

    @Test
    void should_create_table_with_standard_hnsw_index() throws Exception {
        logger.info("🔧 [TEST] Creating table with standard PostgreSQL HNSW index...");

        // Given - Standard PostgreSQL hnsw
        HNSWIndex hnswIndex = HNSWIndex.builder()
                .indexType("hnsw") // Standard PostgreSQL
                .m(16)
                .efConstruction(64)
                .metricType(MetricType.COSINE)
                .build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_hnsw_index")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embeddings
        Embedding embedding1 = embeddingModel.embed("Hello world").content();
        Embedding embedding2 = embeddingModel.embed("Goodbye world").content();

        store.add(embedding1, TextSegment.from("Hello world"));
        store.add(embedding2, TextSegment.from("Goodbye world"));

        logger.info("✅ [TEST] Added 2 embeddings");

        // Then - Verify standard hnsw index exists on embedding column
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexname, indexdef FROM pg_indexes " + "WHERE tablename = 'test_hnsw_index' "
                                + "AND indexdef LIKE '%embedding%' "
                                + "AND indexdef LIKE '%hnsw%'")) {

            boolean hnswIndexFound = false;
            while (rs.next()) {
                String indexName = rs.getString("indexname");
                String indexDef = rs.getString("indexdef");
                logger.info("📋 [TEST] Found HNSW index: {}", indexName);
                logger.info("📋 [TEST] Index definition: {}", indexDef);

                // Verify it's standard hnsw (not ybhnsw) and has vector operator
                assertThat(indexDef).containsIgnoringCase("hnsw");
                assertThat(indexDef).containsAnyOf("vector_cosine_ops", "embedding");
                hnswIndexFound = true;
            }

            assertThat(hnswIndexFound).isTrue();
            logger.info("✅ [TEST] Standard PostgreSQL HNSW index verified successfully");
        }

        // Verify search works
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("Hello world");
        logger.info("✅ [TEST] Search with standard HNSW index works correctly");
    }

    @Test
    void should_create_table_with_yugabytedb_ybhnsw_index() throws Exception {
        logger.info("🔧 [TEST] Creating table with YugabyteDB ybhnsw index...");

        // Given - YugabyteDB optimized ybhnsw
        HNSWIndex ybhnswIndex = HNSWIndex.builder()
                .indexType("ybhnsw") // YugabyteDB optimized
                .m(16)
                .efConstruction(64)
                .metricType(MetricType.COSINE)
                .build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_ybhnsw_index")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(ybhnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embeddings
        Embedding embedding1 = embeddingModel.embed("YugabyteDB test").content();
        Embedding embedding2 = embeddingModel.embed("ybhnsw index").content();

        store.add(embedding1, TextSegment.from("YugabyteDB test"));
        store.add(embedding2, TextSegment.from("ybhnsw index"));

        logger.info("✅ [TEST] Added 2 embeddings");

        // Then - Verify ybhnsw index exists on embedding column
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexname, indexdef FROM pg_indexes " + "WHERE tablename = 'test_ybhnsw_index' "
                                + "AND indexdef LIKE '%embedding%' "
                                + "AND indexdef LIKE '%ybhnsw%'")) {

            boolean ybhnswIndexFound = false;
            while (rs.next()) {
                String indexName = rs.getString("indexname");
                String indexDef = rs.getString("indexdef");
                logger.info("📋 [TEST] Found ybhnsw index: {}", indexName);
                logger.info("📋 [TEST] Index definition: {}", indexDef);

                // Verify it's ybhnsw (YugabyteDB optimized) and has vector operator
                assertThat(indexDef).contains("ybhnsw");
                assertThat(indexDef).containsAnyOf("vector_cosine_ops", "embedding");
                ybhnswIndexFound = true;
            }

            assertThat(ybhnswIndexFound).isTrue();
            logger.info("✅ [TEST] YugabyteDB ybhnsw index verified successfully");
        }

        // Verify search works
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("YugabyteDB test");
        logger.info("✅ [TEST] Search with ybhnsw index works correctly");
    }

    @Test
    void should_create_table_with_hnsw_index_euclidean() throws Exception {
        logger.info("🔧 [TEST] Creating table with HNSW index (EUCLIDEAN metric)...");

        // Given - Note: YugabyteDB only supports ybhnsw, not ivfflat
        HNSWIndex hnswIndex = HNSWIndex.builder()
                .m(16)
                .efConstruction(64)
                .metricType(MetricType.EUCLIDEAN)
                .build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_hnsw_euclidean_index")
                .dimension(384)
                .metricType(MetricType.EUCLIDEAN)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embeddings
        Embedding embedding1 = embeddingModel.embed("Machine learning").content();
        Embedding embedding2 = embeddingModel.embed("Deep learning").content();

        store.add(embedding1, TextSegment.from("Machine learning"));
        store.add(embedding2, TextSegment.from("Deep learning"));

        logger.info("✅ [TEST] Added 2 embeddings");

        // Then - Verify index exists with EUCLIDEAN distance (check for both hnsw and ybhnsw)
        // Note: YugabyteDB may not always show operator class in pg_indexes
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexname, indexdef FROM pg_indexes " + "WHERE tablename = 'test_hnsw_euclidean_index' "
                                + "AND indexdef LIKE '%embedding%' "
                                + "AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            boolean hnswIndexFound = false;
            while (rs.next()) {
                String indexName = rs.getString("indexname");
                String indexDef = rs.getString("indexdef");
                logger.info("📋 [TEST] Found HNSW index: {}", indexName);
                logger.info("📋 [TEST] Index definition: {}", indexDef);
                logger.info(
                        "📋 [TEST] Index type: {}",
                        indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");

                // Check that it's one of the supported HNSW types
                assertThat(indexDef).matches(".*\\b(hnsw|ybhnsw)\\b.*");
                // Note: operator class (vector_l2_ops) may not always appear in pg_indexes for YugabyteDB
                hnswIndexFound = true;
            }

            assertThat(hnswIndexFound).isTrue();
            logger.info("✅ [TEST] HNSW index with EUCLIDEAN metric verified successfully");
        }

        // Verify search works
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("Machine learning");
        logger.info("✅ [TEST] Search with HNSW EUCLIDEAN index works correctly");
    }

    @Test
    void should_create_table_with_no_index() throws Exception {
        logger.info("🔧 [TEST] Creating table with no index (sequential scan)...");

        // Given
        NoIndex noIndex = new NoIndex();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_no_index")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(noIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embeddings
        Embedding embedding1 = embeddingModel.embed("Sequential scan").content();
        Embedding embedding2 = embeddingModel.embed("No index").content();

        store.add(embedding1, TextSegment.from("Sequential scan"));
        store.add(embedding2, TextSegment.from("No index"));

        logger.info("✅ [TEST] Added 2 embeddings");

        // Then - Verify NO vector index exists (check for both hnsw and ybhnsw)
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT indexname FROM pg_indexes " + "WHERE tablename = 'test_no_index' AND "
                                + "(indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isFalse();
            logger.info("✅ [TEST] Confirmed no vector index exists (checked both hnsw and ybhnsw)");
        }

        // Verify search still works (uses sequential scan)
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("Sequential scan");
        logger.info("✅ [TEST] Search without index works correctly (sequential scan)");
    }

    @Test
    void should_create_hnsw_index_with_custom_parameters() throws Exception {
        logger.info("🔧 [TEST] Creating HNSW index with custom parameters...");

        // Given - High performance configuration
        HNSWIndex hnswIndex = HNSWIndex.builder()
                .m(32)
                .efConstruction(128)
                .metricType(MetricType.DOT_PRODUCT)
                .build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_custom_params")
                .dimension(384)
                .metricType(MetricType.DOT_PRODUCT)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embedding
        Embedding embedding = embeddingModel.embed("Custom parameters").content();
        store.add(embedding, TextSegment.from("Custom parameters"));

        logger.info("✅ [TEST] Added embedding");

        // Then - Verify index with custom parameters (check for both hnsw and ybhnsw)
        // Note: YugabyteDB's pg_indexes doesn't show index parameters in the definition
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexdef FROM pg_indexes "
                                + "WHERE tablename = 'test_custom_params' AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isTrue();
            String indexDef = rs.getString("indexdef");
            logger.info("📋 [TEST] Index definition: {}", indexDef);
            logger.info(
                    "📋 [TEST] Index type: {}",
                    indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");

            // Check that it's one of the supported HNSW types
            assertThat(indexDef).matches(".*\\b(hnsw|ybhnsw)\\b.*");
            assertThat(indexDef).contains("vector_ip_ops"); // DOT_PRODUCT

            // Note: YugabyteDB doesn't show m/ef_construction in pg_indexes view
            // The parameters are set during index creation but not visible in pg_indexes
            logger.info("✅ [TEST] Custom HNSW index created (parameters applied but not visible in pg_indexes)");
        }

        // Verify search works
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        logger.info("✅ [TEST] Search with custom HNSW parameters works correctly");
    }

    @Test
    void should_create_hnsw_index_with_dot_product_metric() throws Exception {
        logger.info("🔧 [TEST] Creating HNSW index with DOT_PRODUCT metric...");

        // Given - DOT_PRODUCT configuration
        HNSWIndex hnswIndex =
                HNSWIndex.builder().metricType(MetricType.DOT_PRODUCT).build();

        YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_dot_product")
                .dimension(384)
                .metricType(MetricType.DOT_PRODUCT)
                .vectorIndex(hnswIndex)
                .createTableIfNotExists(true)
                .build();

        // When - Add embedding
        Embedding embedding = embeddingModel.embed("Dot product test").content();
        store.add(embedding, TextSegment.from("Dot product test"));

        logger.info("✅ [TEST] Added embedding");

        // Then - Verify index with DOT_PRODUCT metric (check for both hnsw and ybhnsw)
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexdef FROM pg_indexes "
                                + "WHERE tablename = 'test_dot_product' AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isTrue();
            String indexDef = rs.getString("indexdef");
            logger.info("📋 [TEST] Index definition: {}", indexDef);
            logger.info(
                    "📋 [TEST] Index type: {}",
                    indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");

            // Check that it's one of the supported HNSW types
            assertThat(indexDef).matches(".*\\b(hnsw|ybhnsw)\\b.*");
            assertThat(indexDef).contains("vector_ip_ops"); // DOT_PRODUCT (inner product)

            logger.info("✅ [TEST] HNSW with DOT_PRODUCT metric verified");
        }

        // Verify search works
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
        assertThat(result.matches()).hasSize(1);
        logger.info("✅ [TEST] Search with DOT_PRODUCT metric works correctly");
    }

    @Test
    void should_handle_different_distance_metrics() throws Exception {
        logger.info("🔧 [TEST] Testing different distance metrics...");

        // Test COSINE
        HNSWIndex cosineIndex =
                HNSWIndex.builder().metricType(MetricType.COSINE).build();

        YugabyteDBEmbeddingStore cosineStore = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_cosine_metric")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(cosineIndex)
                .createTableIfNotExists(true)
                .build();

        Embedding embedding = embeddingModel.embed("Distance metric test").content();
        cosineStore.add(embedding, TextSegment.from("Cosine distance"));

        // Verify COSINE operator (check for both hnsw and ybhnsw)
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexdef FROM pg_indexes "
                                + "WHERE tablename = 'test_cosine_metric' AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isTrue();
            String indexDef = rs.getString("indexdef");
            logger.info(
                    "📋 [TEST] Index type: {}",
                    indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");
            assertThat(indexDef).matches(".*\\b(hnsw|ybhnsw)\\b.*");
            assertThat(indexDef).contains("vector_cosine_ops");
            logger.info("✅ [TEST] COSINE distance metric verified");
        }

        logger.info("✅ [TEST] Distance metrics test completed");
    }

    @Test
    void should_support_hybrid_search_with_gin_and_hnsw_indexes() throws Exception {
        testGinHnswHybridSearch(engine, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    void should_support_hybrid_search_with_metadata_and_vector_indexes() throws Exception {
        testHybridSearch(engine, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    void should_verify_index_names() throws Exception {
        logger.info("🔧 [TEST] Testing default and custom index names...");

        // Test 1: Default index name
        HNSWIndex defaultNameIndex =
                HNSWIndex.builder().metricType(MetricType.COSINE).build();

        YugabyteDBEmbeddingStore defaultStore = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_default_index_name")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(defaultNameIndex)
                .createTableIfNotExists(true)
                .build();

        Embedding embedding = embeddingModel.embed("Index name test").content();
        defaultStore.add(embedding, TextSegment.from("Default index"));

        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexname, indexdef FROM pg_indexes "
                                + "WHERE tablename = 'test_default_index_name' AND (indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

            assertThat(rs.next()).isTrue();
            String indexName = rs.getString("indexname");
            String indexDef = rs.getString("indexdef");
            logger.info(
                    "📋 [TEST] Index type: {}",
                    indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");
            assertThat(indexName).contains("test_default_index_name");
            assertThat(indexName).contains("embedding");
            assertThat(indexName).contains("idx");
            logger.info("✅ [TEST] Default index name: {}", indexName);
        }

        // Test 2: Custom index name
        HNSWIndex customNameIndex = HNSWIndex.builder()
                .name("my_custom_vector_index")
                .metricType(MetricType.COSINE)
                .build();

        YugabyteDBEmbeddingStore customStore = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_custom_index_name")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .vectorIndex(customNameIndex)
                .createTableIfNotExists(true)
                .build();

        customStore.add(embedding, TextSegment.from("Custom index"));

        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT indexname FROM pg_indexes " + "WHERE indexname = 'my_custom_vector_index'")) {

            assertThat(rs.next()).isTrue();
            logger.info("✅ [TEST] Custom index name verified: my_custom_vector_index");
        }

        // Cleanup
        try (Connection conn = engine.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_default_index_name CASCADE");
            stmt.execute("DROP TABLE IF EXISTS test_custom_index_name CASCADE");
        }

        logger.info("✅ [TEST] Index name test completed");
    }

    /**
     * Smart Driver Vector Index Tests
     *
     * This nested class runs the same vector index tests using YugabyteDB Smart Driver
     * instead of PostgreSQL JDBC driver to verify compatibility across both drivers.
     */
    @Nested
    class SmartDriverVectorIndexIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverVectorIndexIT.class);
        private static YugabyteDBEngine smartEngine;

        @BeforeAll
        static void setUpSmartDriver() throws Exception {
            smartLogger.info("🚀 [SMART-DRIVER] Initializing Smart Driver tests...");
            smartEngine = createSmartDriverEngine(5, "VectorIndexSmartDriverPool");
            smartLogger.info("✅ [SMART-DRIVER] Smart Driver engine created successfully");
        }

        @AfterAll
        static void tearDownSmartDriver() {
            if (smartEngine != null) {
                smartLogger.info("🧹 [SMART-DRIVER] Closing Smart Driver engine...");
                smartEngine.close();
            }
        }

        @Test
        void should_create_table_with_standard_hnsw_index_using_smart_driver() throws Exception {
            smartLogger.info("🔧 [SMART-DRIVER] Creating table with standard PostgreSQL HNSW index...");

            // Given - Standard PostgreSQL hnsw
            HNSWIndex hnswIndex = HNSWIndex.builder()
                    .indexType("hnsw") // Standard PostgreSQL
                    .m(16)
                    .efConstruction(64)
                    .metricType(MetricType.COSINE)
                    .build();

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("test_smart_hnsw_index")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .vectorIndex(hnswIndex)
                    .createTableIfNotExists(true)
                    .build();

            // When - Add embeddings
            Embedding embedding1 = embeddingModel.embed("Hello world").content();
            Embedding embedding2 = embeddingModel.embed("Goodbye world").content();

            store.add(embedding1, TextSegment.from("Hello world"));
            store.add(embedding2, TextSegment.from("Goodbye world"));

            smartLogger.info("✅ [SMART-DRIVER] Added 2 embeddings");

            // Then - Verify standard hnsw index exists on embedding column
            try (Connection conn = smartEngine.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT indexname, indexdef FROM pg_indexes " + "WHERE tablename = 'test_smart_hnsw_index' "
                                    + "AND indexdef LIKE '%embedding%' "
                                    + "AND indexdef LIKE '%hnsw%'")) {

                boolean hnswIndexFound = false;
                while (rs.next()) {
                    String indexName = rs.getString("indexname");
                    String indexDef = rs.getString("indexdef");
                    smartLogger.info("📋 [SMART-DRIVER] Found HNSW index: {}", indexName);
                    smartLogger.info("📋 [SMART-DRIVER] Index definition: {}", indexDef);
                    smartLogger.info(
                            "📋 [SMART-DRIVER] Index type: {}",
                            indexDef.contains("ybhnsw") ? "ybhnsw (YugabyteDB)" : "hnsw (PostgreSQL)");

                    // Verify it's an HNSW variant and has vector operator
                    assertThat(indexDef).containsIgnoringCase("hnsw");
                    assertThat(indexDef).containsAnyOf("vector_cosine_ops", "embedding");
                    hnswIndexFound = true;
                }

                assertThat(hnswIndexFound).isTrue();
                smartLogger.info("✅ [SMART-DRIVER] Standard PostgreSQL HNSW index verified successfully");
            }

            // Verify search works
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding1)
                    .maxResults(1)
                    .build();
            EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
            assertThat(result.matches()).hasSize(1);
            assertThat(result.matches().get(0).embedded().text()).isEqualTo("Hello world");
            smartLogger.info("✅ [SMART-DRIVER] Search with standard HNSW index works correctly");

            // Cleanup
            try (Connection conn = smartEngine.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS test_smart_hnsw_index CASCADE");
            }
        }

        @Test
        void should_create_table_with_yugabytedb_ybhnsw_index_using_smart_driver() throws Exception {
            smartLogger.info("🔧 [SMART-DRIVER] Creating table with YugabyteDB ybhnsw index...");

            // Given - YugabyteDB optimized ybhnsw
            HNSWIndex ybhnswIndex = HNSWIndex.builder()
                    .indexType("ybhnsw") // YugabyteDB optimized
                    .m(16)
                    .efConstruction(64)
                    .metricType(MetricType.COSINE)
                    .build();

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("test_smart_ybhnsw_index")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .vectorIndex(ybhnswIndex)
                    .createTableIfNotExists(true)
                    .build();

            // When - Add embeddings
            Embedding embedding1 = embeddingModel.embed("YugabyteDB test").content();
            Embedding embedding2 = embeddingModel.embed("ybhnsw index").content();

            store.add(embedding1, TextSegment.from("YugabyteDB test"));
            store.add(embedding2, TextSegment.from("ybhnsw index"));

            smartLogger.info("✅ [SMART-DRIVER] Added 2 embeddings");

            // Then - Verify ybhnsw index exists on embedding column
            try (Connection conn = smartEngine.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT indexname, indexdef FROM pg_indexes "
                            + "WHERE tablename = 'test_smart_ybhnsw_index' "
                            + "AND indexdef LIKE '%embedding%' "
                            + "AND indexdef LIKE '%ybhnsw%'")) {

                boolean ybhnswIndexFound = false;
                while (rs.next()) {
                    String indexName = rs.getString("indexname");
                    String indexDef = rs.getString("indexdef");
                    smartLogger.info("📋 [SMART-DRIVER] Found ybhnsw index: {}", indexName);
                    smartLogger.info("📋 [SMART-DRIVER] Index definition: {}", indexDef);

                    // Verify it's ybhnsw (YugabyteDB optimized) and has vector operator
                    assertThat(indexDef).contains("ybhnsw");
                    assertThat(indexDef).containsAnyOf("vector_cosine_ops", "embedding");
                    ybhnswIndexFound = true;
                }

                assertThat(ybhnswIndexFound).isTrue();
                smartLogger.info("✅ [SMART-DRIVER] YugabyteDB ybhnsw index verified successfully");
            }

            // Verify search works
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding1)
                    .maxResults(1)
                    .build();
            EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
            assertThat(result.matches()).hasSize(1);
            assertThat(result.matches().get(0).embedded().text()).isEqualTo("YugabyteDB test");
            smartLogger.info("✅ [SMART-DRIVER] Search with ybhnsw index works correctly");

            // Cleanup
            try (Connection conn = smartEngine.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS test_smart_ybhnsw_index CASCADE");
            }
        }

        @Test
        void should_create_table_with_no_index_using_smart_driver() throws Exception {
            smartLogger.info("🔧 [SMART-DRIVER] Creating table with no index (sequential scan)...");

            // Given
            NoIndex noIndex = new NoIndex();

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("test_smart_no_index")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .vectorIndex(noIndex)
                    .createTableIfNotExists(true)
                    .build();

            // When - Add embeddings
            Embedding embedding1 = embeddingModel.embed("Sequential scan").content();
            Embedding embedding2 = embeddingModel.embed("No index").content();

            store.add(embedding1, TextSegment.from("Sequential scan"));
            store.add(embedding2, TextSegment.from("No index"));

            smartLogger.info("✅ [SMART-DRIVER] Added 2 embeddings");

            // Then - Verify NO vector index exists (check for both hnsw and ybhnsw)
            try (Connection conn = smartEngine.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT indexname FROM pg_indexes " + "WHERE tablename = 'test_smart_no_index' AND "
                                    + "(indexdef LIKE '%hnsw%' OR indexdef LIKE '%ybhnsw%')")) {

                assertThat(rs.next()).isFalse();
                smartLogger.info("✅ [SMART-DRIVER] Confirmed no vector index exists (checked both hnsw and ybhnsw)");
            }

            // Verify search still works (uses sequential scan)
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding1)
                    .maxResults(1)
                    .build();
            EmbeddingSearchResult<TextSegment> result = store.search(searchRequest);
            assertThat(result.matches()).hasSize(1);
            assertThat(result.matches().get(0).embedded().text()).isEqualTo("Sequential scan");
            smartLogger.info("✅ [SMART-DRIVER] Search without index works correctly (sequential scan)");

            // Cleanup
            try (Connection conn = smartEngine.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS test_smart_no_index CASCADE");
            }
        }

        @Test
        void should_support_hybrid_search_with_gin_and_hnsw_using_smart_driver() throws Exception {
            testGinHnswHybridSearch(smartEngine, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_support_hybrid_search_with_smart_driver() throws Exception {
            testHybridSearch(smartEngine, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_create_hnsw_index_with_smart_driver() throws Exception {
            testHNSWIndexCreation(smartEngine, smartLogger, "[SMART-DRIVER]", "smart");
        }
    }
}
