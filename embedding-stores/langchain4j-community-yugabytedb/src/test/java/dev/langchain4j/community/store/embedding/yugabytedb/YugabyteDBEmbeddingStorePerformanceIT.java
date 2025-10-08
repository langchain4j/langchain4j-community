package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance tests for YugabyteDBEmbeddingStore.
 * Tests both PostgreSQL JDBC driver (recommended) and YugabyteDB Smart Driver.
 * These tests verify that the store can handle various performance scenarios
 * with real vector operations using Testcontainers.
 */
class YugabyteDBEmbeddingStorePerformanceIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBEmbeddingStorePerformanceIT.class);

    @Test
    void should_handle_bulk_insertions() {
        logger.info("🚀 [TEST] Starting bulk insertion performance test...");
        logger.info("🔧 [TEST] Driver Type: PostgreSQL JDBC Driver (inherited from YugabyteDBTestBase)");

        YugabyteDBEmbeddingStore store = createStore("bulk_test");

        // Generate test data
        int batchSize = 100;
        logger.info("📋 [TEST] Preparing {} embeddings for bulk insertion...", batchSize);

        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> textSegments = new ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            ids.add(randomUUID());
            embeddings.add(embeddingModel.embed("Test document " + i).content());
            textSegments.add(TextSegment.from("Test document content " + i));
        }

        logger.info("📦 [TEST] Performing bulk insertion...");
        long startTime = System.currentTimeMillis();

        store.addAll(ids, embeddings, textSegments);

        long endTime = System.currentTimeMillis();
        long insertionTime = endTime - startTime;

        logger.info(
                "✅ [TEST] Bulk insertion: {} embeddings in {} ms ({} embeddings/sec)",
                batchSize,
                insertionTime,
                String.format("%.2f", (batchSize * 1000.0) / insertionTime));

        // Verify insertions
        logger.info("🔍 [TEST] Verifying insertions with similarity search...");
        EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(0))
                .maxResults(5)
                .build());

        assertThat(searchResult.matches()).isNotEmpty();
        logger.info(
                "✅ [TEST] Found {} matches in search verification",
                searchResult.matches().size());

        logger.info("✅ [TEST] Bulk insertion performance test completed successfully");
    }

    @Test
    void should_handle_concurrent_operations() throws InterruptedException {
        logger.info("🚀 [TEST] Starting concurrent operations performance test...");

        YugabyteDBEmbeddingStore store = createStore("concurrent_test");

        int threadCount = 5;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        logger.info(
                "📋 [TEST] Concurrent test configuration: {} threads × {} ops = {} total operations",
                threadCount,
                operationsPerThread,
                threadCount * operationsPerThread);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        logger.info("⚡ [TEST] Starting concurrent insertions...");
        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String id = randomUUID();
                            Embedding embedding = embeddingModel
                                    .embed("Concurrent test document " + threadId + "-" + i)
                                    .content();
                            TextSegment textSegment = TextSegment.from("Concurrent content " + threadId + "-" + i);

                            store.add(id, embedding, textSegment);
                        }
                    },
                    executor);

            futures.add(future);
        }

        // Wait for all operations to complete
        logger.info("[TEST] Waiting for all concurrent operations to complete...");
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int totalOperations = threadCount * operationsPerThread;

        executor.shutdown();

        logger.info(
                "✅ [TEST] Concurrent operations: {} insertions in {} ms ({} ops/sec)",
                totalOperations,
                totalTime,
                String.format("%.2f", (totalOperations * 1000.0) / totalTime));

        // Verify some insertions worked
        logger.info("🔍 [TEST] Verifying concurrent insertions...");
        Embedding testEmbedding =
                embeddingModel.embed("Concurrent test document 0-0").content();
        EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(testEmbedding)
                .maxResults(10)
                .build());

        assertThat(searchResult.matches()).isNotEmpty();
        logger.info(
                "✅ [TEST] Found {} matches in concurrent verification",
                searchResult.matches().size());

        logger.info("✅ [TEST] Concurrent operations performance test completed");
    }

    @Test
    void should_perform_fast_similarity_search() {
        logger.info("🚀 [TEST] Starting similarity search performance test...");

        YugabyteDBEmbeddingStore store = createStore("search_perf_test");

        // Insert test dataset
        int datasetSize = 200;
        logger.info("🚀 Preparing {} documents for search performance test...", datasetSize);

        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> textSegments = new ArrayList<>();

        for (int i = 0; i < datasetSize; i++) {
            ids.add(randomUUID());
            embeddings.add(embeddingModel
                    .embed("Search test document " + i + " with content about topic " + (i % 10))
                    .content());
            textSegments.add(TextSegment.from("Search document content " + i));
        }

        logger.info("📦 Inserting dataset for search performance test...");
        store.addAll(ids, embeddings, textSegments);

        // Perform search performance test
        logger.info("🔍 [TEST] Testing search performance...");
        Embedding queryEmbedding = embeddingModel
                .embed("Search test document 0 with content about topic 0")
                .content();

        long startTime = System.currentTimeMillis();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20)
                .minScore(0.1)
                .build());

        long endTime = System.currentTimeMillis();
        long searchTime = endTime - startTime;

        logger.info(
                "✅ [TEST] Search performance: found {} matches in {} ms",
                searchResult.matches().size(),
                searchTime);

        assertThat(searchResult.matches()).isNotEmpty();
        assertThat(searchTime).isLessThan(5000); // Should complete within 5 seconds

        logger.info("✅ [TEST] Similarity search performance test completed");
    }

    @Test
    void should_handle_large_embeddings() {
        logger.info("🚀 [TEST] Starting large embeddings performance test...");

        // Create store with larger dimension for this test
        YugabyteDBEmbeddingStore store = createStore("large_embedding_test", 768, MetricType.COSINE);

        int embeddingCount = 50;
        logger.info("🚀 Preparing {} large embeddings (768-dimensional)...", embeddingCount);

        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> textSegments = new ArrayList<>();

        // Generate larger embeddings by repeating the base embedding
        for (int i = 0; i < embeddingCount; i++) {
            Embedding baseEmbedding = embeddingModel
                    .embed("Large embedding test document. YugabyteDB's " + i)
                    .content();

            // Expand to 768 dimensions by repeating and normalizing
            List<Float> expandedVector = new ArrayList<>();
            List<Float> baseVector = baseEmbedding.vectorAsList();

            // Repeat the base vector to reach 768 dimensions
            while (expandedVector.size() < 768) {
                for (Float value : baseVector) {
                    if (expandedVector.size() < 768) {
                        expandedVector.add(value);
                    }
                }
            }

            ids.add(randomUUID());
            embeddings.add(Embedding.from(expandedVector));
            textSegments.add(TextSegment.from("Large embedding content " + i));
        }

        logger.info("📦 Inserting large embeddings...");
        long startTime = System.currentTimeMillis();

        store.addAll(ids, embeddings, textSegments);

        long endTime = System.currentTimeMillis();
        long insertionTime = endTime - startTime;

        logger.info(
                "✅ [TEST] Large embeddings insertion: {} embeddings (768-dim) in {} ms ({} embeddings/sec)",
                embeddingCount,
                insertionTime,
                String.format("%.2f", (embeddingCount * 1000.0) / insertionTime));

        // Test search with large embeddings
        logger.info("🔍 [TEST] Testing search with large embeddings...");
        startTime = System.currentTimeMillis();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(0))
                .maxResults(10)
                .build());

        endTime = System.currentTimeMillis();
        long searchTime = endTime - startTime;

        logger.info(
                "✅ [TEST] Large embedding search: found {} matches in {} ms",
                searchResult.matches().size(),
                searchTime);

        assertThat(searchResult.matches()).isNotEmpty();

        logger.info("✅ [TEST] Large embeddings performance test completed");
    }

    @Test
    void should_handle_massive_dataset() {
        logger.info("🚀 [TEST] Starting massive dataset performance test...");

        YugabyteDBEmbeddingStore store = createStore("massive_test");

        int totalDocuments = 1000;
        int batchSize = 200;
        int batches = totalDocuments / batchSize;

        logger.info("🚀 Preparing massive dataset: {} documents...", totalDocuments);

        long totalInsertionTime = 0;

        for (int batch = 0; batch < batches; batch++) {
            logger.info("📦 Preparing batch {}/{} ({} documents)...", batch + 1, batches, batchSize);

            List<String> ids = new ArrayList<>();
            List<Embedding> embeddings = new ArrayList<>();
            List<TextSegment> textSegments = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                int docId = batch * batchSize + i;
                ids.add(randomUUID());
                embeddings.add(embeddingModel
                        .embed("Massive dataset document " + docId + " category " + (docId % 20))
                        .content());
                textSegments.add(TextSegment.from("Massive dataset content " + docId));
            }

            long startTime = System.currentTimeMillis();
            store.addAll(ids, embeddings, textSegments);
            long endTime = System.currentTimeMillis();

            long batchTime = endTime - startTime;
            totalInsertionTime += batchTime;

            logger.info(
                    "✅ [TEST] Batch {}: {} embeddings in {} ms ({} embeddings/sec)",
                    batch + 1,
                    batchSize,
                    batchTime,
                    String.format("%.2f", (batchSize * 1000.0) / batchTime));
        }

        logger.info(
                "✅ [TEST] Massive dataset insertion: {} total embeddings in {} ms ({} embeddings/sec)",
                totalDocuments,
                totalInsertionTime,
                String.format("%.2f", (totalDocuments * 1000.0) / totalInsertionTime));

        // Test search performance on massive dataset
        logger.info("🔍 [TEST] Testing search performance on massive dataset...");
        Embedding queryEmbedding =
                embeddingModel.embed("Massive dataset document 0 category 0").content();

        long startTime = System.currentTimeMillis();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(50)
                .minScore(0.1)
                .build());

        long endTime = System.currentTimeMillis();
        long searchTime = endTime - startTime;

        logger.info(
                "✅ [TEST] Massive dataset search: found {} matches in {} ms",
                searchResult.matches().size(),
                searchTime);

        assertThat(searchResult.matches()).isNotEmpty();
        assertThat(searchTime).isLessThan(10000); // Should complete within 10 seconds

        logger.info("✅ [TEST] Massive dataset performance test completed");
    }

    @AfterEach
    void finalCleanup() {
        logger.info("🧹 [CLEANUP] PostgreSQL driver performance test cleanup...");
        dropTestTables("bulk_test", "concurrent_test", "search_perf_test", "large_embedding_test", "massive_test");
        logger.info("✅ [CLEANUP] PostgreSQL driver performance test cleanup completed");
    }

    /**
     * Nested test class for YugabyteDB Smart Driver performance tests.
     * Runs all performance tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    class SmartDriverPerformanceIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverPerformanceIT.class);
        private static YugabyteDBEngine smartDriverEngine;

        @BeforeAll
        static void setupSmartDriver() throws Exception {
            smartLogger.info("🚀 [SMART-DRIVER-SETUP] Initializing Smart Driver for performance tests...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER-SETUP] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            smartDriverEngine = createSmartDriverEngine(20, "SmartDriverPerformancePool");
            smartLogger.info("✅ [SMART-DRIVER-SETUP] Smart Driver engine created successfully");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER-CLEANUP] Starting Smart Driver cleanup...");

            if (smartDriverEngine != null) {
                smartLogger.info("[SMART-DRIVER-CLEANUP] Closing Smart Driver engine...");
                smartDriverEngine.close();
            }

            smartLogger.info("✅ [SMART-DRIVER-CLEANUP] Smart Driver cleanup completed");
        }

        private YugabyteDBEmbeddingStore createSmartDriverStore(String tableName) {
            return YugabyteDBEmbeddingStore.builder()
                    .engine(smartDriverEngine)
                    .schema(YugabyteDBSchema.builder()
                            .schemaName("public")
                            .tableName(tableName)
                            .dimension(384)
                            .metricType(MetricType.COSINE)
                            .createTableIfNotExists(true)
                            .build())
                    .build();
        }

        private YugabyteDBEmbeddingStore createSmartDriverStore(
                String tableName, int dimension, MetricType metricType) {
            return YugabyteDBEmbeddingStore.builder()
                    .engine(smartDriverEngine)
                    .schema(YugabyteDBSchema.builder()
                            .schemaName("public")
                            .tableName(tableName)
                            .dimension(dimension)
                            .metricType(metricType)
                            .createTableIfNotExists(true)
                            .build())
                    .build();
        }

        @Test
        void should_handle_bulk_insertions_with_smart_driver() {
            smartLogger.info("🚀 [SMART-DRIVER] Starting bulk insertion performance test...");

            YugabyteDBEmbeddingStore store = createSmartDriverStore("bulk_test_smart");

            // Generate test data
            int batchSize = 100;
            smartLogger.info("📋 [SMART-DRIVER] Preparing {} embeddings for bulk insertion...", batchSize);

            List<String> ids = new ArrayList<>();
            List<Embedding> embeddings = new ArrayList<>();
            List<TextSegment> textSegments = new ArrayList<>();

            for (int i = 0; i < batchSize; i++) {
                ids.add(randomUUID());
                embeddings.add(embeddingModel.embed("Test document " + i).content());
                textSegments.add(TextSegment.from("Test document content " + i));
            }

            smartLogger.info("📦 [SMART-DRIVER] Performing bulk insertion...");
            long startTime = System.currentTimeMillis();

            store.addAll(ids, embeddings, textSegments);

            long endTime = System.currentTimeMillis();
            long insertionTime = endTime - startTime;

            smartLogger.info(
                    "✅ [SMART-DRIVER] Bulk insertion: {} embeddings in {} ms ({} embeddings/sec)",
                    batchSize,
                    insertionTime,
                    String.format("%.2f", (batchSize * 1000.0) / insertionTime));

            // Verify insertions
            smartLogger.info("🔍 [SMART-DRIVER] Verifying insertions with similarity search...");
            EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddings.get(0))
                    .maxResults(5)
                    .build());

            assertThat(searchResult.matches()).isNotEmpty();
            smartLogger.info(
                    "✅ [SMART-DRIVER] Found {} matches in search verification",
                    searchResult.matches().size());

            smartLogger.info("✅ [SMART-DRIVER] Bulk insertion performance test completed successfully");
        }

        @Test
        void should_handle_concurrent_operations_with_smart_driver() throws InterruptedException {
            smartLogger.info("🚀 [SMART-DRIVER] Starting concurrent operations performance test...");

            YugabyteDBEmbeddingStore store = createSmartDriverStore("concurrent_test_smart");

            int threadCount = 5;
            int operationsPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            smartLogger.info(
                    "📋 [SMART-DRIVER] Concurrent test configuration: {} threads × {} ops = {} total operations",
                    threadCount,
                    operationsPerThread,
                    threadCount * operationsPerThread);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            smartLogger.info("⚡ [SMART-DRIVER] Starting concurrent insertions...");
            long startTime = System.currentTimeMillis();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> {
                            for (int i = 0; i < operationsPerThread; i++) {
                                String id = randomUUID();
                                Embedding embedding = embeddingModel
                                        .embed("Concurrent test document " + threadId + "-" + i)
                                        .content();
                                TextSegment textSegment = TextSegment.from("Concurrent content " + threadId + "-" + i);

                                store.add(id, embedding, textSegment);
                            }
                        },
                        executor);

                futures.add(future);
            }

            // Wait for all operations to complete
            smartLogger.info("[SMART-DRIVER] Waiting for all concurrent operations to complete...");
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            int totalOperations = threadCount * operationsPerThread;

            executor.shutdown();

            smartLogger.info(
                    "✅ [SMART-DRIVER] Concurrent operations: {} insertions in {} ms ({} ops/sec)",
                    totalOperations,
                    totalTime,
                    String.format("%.2f", (totalOperations * 1000.0) / totalTime));

            // Verify some insertions worked
            smartLogger.info("🔍 [SMART-DRIVER] Verifying concurrent insertions...");
            Embedding testEmbedding =
                    embeddingModel.embed("Concurrent test document 0-0").content();
            EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(testEmbedding)
                    .maxResults(10)
                    .build());

            assertThat(searchResult.matches()).isNotEmpty();
            smartLogger.info(
                    "✅ [SMART-DRIVER] Found {} matches in concurrent verification",
                    searchResult.matches().size());

            smartLogger.info("✅ [SMART-DRIVER] Concurrent operations performance test completed");
        }

        @Test
        void should_perform_fast_similarity_search_with_smart_driver() {
            smartLogger.info("🚀 [SMART-DRIVER] Starting similarity search performance test...");

            YugabyteDBEmbeddingStore store = createSmartDriverStore("search_perf_test_smart");

            // Insert test dataset
            int datasetSize = 200;
            smartLogger.info("📋 [SMART-DRIVER] Preparing {} documents for search performance test...", datasetSize);

            List<String> ids = new ArrayList<>();
            List<Embedding> embeddings = new ArrayList<>();
            List<TextSegment> textSegments = new ArrayList<>();

            for (int i = 0; i < datasetSize; i++) {
                ids.add(randomUUID());
                embeddings.add(embeddingModel
                        .embed("Search test document " + i + " with content about topic " + (i % 10))
                        .content());
                textSegments.add(TextSegment.from("Search document content " + i));
            }

            smartLogger.info("📦 [SMART-DRIVER] Inserting dataset for search performance test...");
            store.addAll(ids, embeddings, textSegments);

            // Perform search performance test
            smartLogger.info("🔍 [SMART-DRIVER] Testing search performance...");
            Embedding queryEmbedding = embeddingModel
                    .embed("Search test document 0 with content about topic 0")
                    .content();

            long startTime = System.currentTimeMillis();

            EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(20)
                    .minScore(0.1)
                    .build());

            long endTime = System.currentTimeMillis();
            long searchTime = endTime - startTime;

            smartLogger.info(
                    "✅ [SMART-DRIVER] Search performance: found {} matches in {} ms",
                    searchResult.matches().size(),
                    searchTime);

            assertThat(searchResult.matches()).isNotEmpty();
            assertThat(searchTime).isLessThan(5000); // Should complete within 5 seconds

            smartLogger.info("✅ [SMART-DRIVER] Similarity search performance test completed");
        }

        @Test
        void should_handle_large_embeddings_with_smart_driver() {
            smartLogger.info("🚀 [SMART-DRIVER] Starting large embeddings performance test...");

            // Create store with larger dimension for this test
            YugabyteDBEmbeddingStore store =
                    createSmartDriverStore("large_embedding_test_smart", 768, MetricType.COSINE);

            int embeddingCount = 50;
            smartLogger.info("📋 [SMART-DRIVER] Preparing {} large embeddings (768-dimensional)...", embeddingCount);

            List<String> ids = new ArrayList<>();
            List<Embedding> embeddings = new ArrayList<>();
            List<TextSegment> textSegments = new ArrayList<>();

            // Generate larger embeddings by repeating the base embedding
            for (int i = 0; i < embeddingCount; i++) {
                Embedding baseEmbedding = embeddingModel
                        .embed("Large embedding test document " + i)
                        .content();

                // Expand to 768 dimensions by repeating and normalizing
                List<Float> expandedVector = new ArrayList<>();
                List<Float> baseVector = baseEmbedding.vectorAsList();

                // Repeat the base vector to reach 768 dimensions
                while (expandedVector.size() < 768) {
                    for (Float value : baseVector) {
                        if (expandedVector.size() < 768) {
                            expandedVector.add(value);
                        }
                    }
                }

                ids.add(randomUUID());
                embeddings.add(Embedding.from(expandedVector));
                textSegments.add(TextSegment.from("Large embedding content " + i));
            }

            smartLogger.info("📦 [SMART-DRIVER] Inserting large embeddings...");
            long startTime = System.currentTimeMillis();

            store.addAll(ids, embeddings, textSegments);

            long endTime = System.currentTimeMillis();
            long insertionTime = endTime - startTime;

            smartLogger.info(
                    "✅ [SMART-DRIVER] Large embeddings insertion: {} embeddings (768-dim) in {} ms ({} embeddings/sec)",
                    embeddingCount,
                    insertionTime,
                    String.format("%.2f", (embeddingCount * 1000.0) / insertionTime));

            // Test search with large embeddings
            smartLogger.info("🔍 [SMART-DRIVER] Testing search with large embeddings...");
            startTime = System.currentTimeMillis();

            EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddings.get(0))
                    .maxResults(10)
                    .build());

            endTime = System.currentTimeMillis();
            long searchTime = endTime - startTime;

            smartLogger.info(
                    "✅ [SMART-DRIVER] Large embedding search: found {} matches in {} ms",
                    searchResult.matches().size(),
                    searchTime);

            assertThat(searchResult.matches()).isNotEmpty();

            smartLogger.info("✅ [SMART-DRIVER] Large embeddings performance test completed");
        }

        @Test
        void should_handle_massive_dataset_with_smart_driver() {
            smartLogger.info("🚀 [SMART-DRIVER] Starting massive dataset performance test...");

            YugabyteDBEmbeddingStore store = createSmartDriverStore("massive_test_smart");

            int totalDocuments = 1000;
            int batchSize = 200;
            int batches = totalDocuments / batchSize;

            smartLogger.info("📋 [SMART-DRIVER] Preparing massive dataset: {} documents...", totalDocuments);

            long totalInsertionTime = 0;

            for (int batch = 0; batch < batches; batch++) {
                smartLogger.info(
                        "📦 [SMART-DRIVER] Preparing batch {}/{} ({} documents)...", batch + 1, batches, batchSize);

                List<String> ids = new ArrayList<>();
                List<Embedding> embeddings = new ArrayList<>();
                List<TextSegment> textSegments = new ArrayList<>();

                for (int i = 0; i < batchSize; i++) {
                    int docId = batch * batchSize + i;
                    ids.add(randomUUID());
                    embeddings.add(embeddingModel
                            .embed("Massive dataset document " + docId + " category " + (docId % 20))
                            .content());
                    textSegments.add(TextSegment.from("Massive dataset content " + docId));
                }

                long startTime = System.currentTimeMillis();
                store.addAll(ids, embeddings, textSegments);
                long endTime = System.currentTimeMillis();

                long batchTime = endTime - startTime;
                totalInsertionTime += batchTime;

                smartLogger.info(
                        "✅ [SMART-DRIVER] Batch {}: {} embeddings in {} ms ({} embeddings/sec)",
                        batch + 1,
                        batchSize,
                        batchTime,
                        String.format("%.2f", (batchSize * 1000.0) / batchTime));
            }

            smartLogger.info(
                    "✅ [SMART-DRIVER] Massive dataset insertion: {} total embeddings in {} ms ({} embeddings/sec)",
                    totalDocuments,
                    totalInsertionTime,
                    String.format("%.2f", (totalDocuments * 1000.0) / totalInsertionTime));

            // Test search performance on massive dataset
            smartLogger.info("🔍 [SMART-DRIVER] Testing search performance on massive dataset...");
            Embedding queryEmbedding = embeddingModel
                    .embed("Massive dataset document 0 category 0")
                    .content();

            long startTime = System.currentTimeMillis();

            EmbeddingSearchResult<TextSegment> searchResult = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(50)
                    .minScore(0.1)
                    .build());

            long endTime = System.currentTimeMillis();
            long searchTime = endTime - startTime;

            smartLogger.info(
                    "✅ [SMART-DRIVER] Massive dataset search: found {} matches in {} ms",
                    searchResult.matches().size(),
                    searchTime);

            assertThat(searchResult.matches()).isNotEmpty();
            assertThat(searchTime).isLessThan(10000); // Should complete within 10 seconds

            smartLogger.info("✅ [SMART-DRIVER] Massive dataset performance test completed");
        }

        @AfterEach
        void finalSmartDriverCleanup() {
            smartLogger.info("🧹 [SMART-DRIVER-CLEANUP] Smart Driver performance test cleanup...");
            dropTestTables(
                    "bulk_test_smart",
                    "concurrent_test_smart",
                    "search_perf_test_smart",
                    "large_embedding_test_smart",
                    "massive_test_smart");
            smartLogger.info("✅ [SMART-DRIVER-CLEANUP] Smart Driver performance test cleanup completed");
        }
    }
}
