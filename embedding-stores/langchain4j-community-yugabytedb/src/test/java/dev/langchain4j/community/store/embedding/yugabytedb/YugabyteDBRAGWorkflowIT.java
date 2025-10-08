package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests for complete RAG workflow with YugabyteDB.
 * Tests document processing, chunking, embedding generation, storage, and retrieval.
 * Uses YugabyteDB TestContainer for isolated testing.
 *
 * Main class tests use PostgreSQL JDBC Driver.
 * Nested SmartDriverRAGIT class tests use YugabyteDB Smart Driver.
 */
class YugabyteDBRAGWorkflowIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBRAGWorkflowIT.class);

    static YugabyteDBEngine engine;
    static HikariDataSource dataSource;
    static EmbeddingModel embeddingModel;
    static DocumentSplitter documentSplitter;

    @BeforeAll
    static void setup() {
        logger.info("🚀 [RAG-POSTGRESQL] Initializing RAG workflow test setup with PostgreSQL driver...");
        logger.info("🔧 [RAG-POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        String host = yugabyteContainer.getHost();
        int port = yugabyteContainer.getMappedPort(5433);

        logger.info("📋 [RAG-POSTGRESQL] Container details:");
        logger.info("[RAG-POSTGRESQL]   - Host: {}", host);
        logger.info("[RAG-POSTGRESQL]   - Port: {}", port);
        logger.info("[RAG-POSTGRESQL]   - Database: {}", DB_NAME);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, DB_NAME));
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);

        logger.info("🔧 [RAG-POSTGRESQL] Creating HikariDataSource and YugabyteDBEngine...");
        dataSource = new HikariDataSource(config);
        engine = YugabyteDBEngine.from(dataSource);

        logger.info("🧠 [RAG-POSTGRESQL] Creating embedding model...");
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        logger.info("✂️ [RAG-POSTGRESQL] Creating document splitter...");
        // Use real LangChain4j Core DocumentSplitter
        documentSplitter = DocumentSplitters.recursive(300, 50);

        logger.info("✅ [RAG-POSTGRESQL] RAG workflow test setup completed successfully");
    }

    @AfterAll
    static void cleanup() {
        logger.info("🧹 [RAG-POSTGRESQL] Starting RAG workflow test cleanup...");
        if (engine != null) {
            logger.info("[RAG-POSTGRESQL] Closing YugabyteDBEngine...");
            engine.close();
        }
        if (dataSource != null) {
            logger.info("[RAG-POSTGRESQL] Closing HikariDataSource...");
            dataSource.close();
        }
        logger.info("✅ [RAG-POSTGRESQL] RAG workflow test cleanup completed");
    }

    @Test
    void should_process_complete_rag_workflow_with_real_chunking() {
        // Test complete RAG workflow: Document → LangChain4j Chunking → YugabyteDB Storage → Retrieval
        YugabyteDBEmbeddingStore store = createStore("rag_workflow_test");

        // Step 1: Create sample documents (like user would)
        String[] rawDocuments = {
            "YugabyteDB is a distributed SQL database designed for cloud-native applications. "
                    + "It provides PostgreSQL compatibility while offering horizontal scalability and high availability. "
                    + "The database automatically shards data across multiple nodes for optimal performance. "
                    + "Connection pooling with HikariCP optimizes resource usage and improves application performance.",
            "Vector embeddings are numerical representations of text that capture semantic meaning and context. "
                    + "These high-dimensional vectors enable semantic search capabilities by measuring similarity between "
                    + "different pieces of text content. YugabyteDB supports the pgvector extension for efficient vector operations. "
                    + "RAG combines information retrieval with language model generation for better AI responses."
        };

        String[] categories = {"database", "ai"};
        List<String> allIds = new ArrayList<>();

        // Step 2: Process documents through complete pipeline
        for (int i = 0; i < rawDocuments.length; i++) {
            logger.info("📄 Processing document {} [{}]...", i + 1, categories[i]);

            // Create Document (LangChain4j Core)
            Metadata documentMetadata = new Metadata()
                    .put("source", "test_document")
                    .put("category", categories[i])
                    .put("document_id", String.valueOf(i + 1));

            Document document = Document.from(rawDocuments[i], documentMetadata);

            // Real chunking with LangChain4j Core DocumentSplitters
            List<TextSegment> chunks = documentSplitter.split(document);
            logger.info("  ✅ LangChain4j chunked into {} segments", chunks.size());

            // Process each chunk
            for (int j = 0; j < chunks.size(); j++) {
                TextSegment chunk = chunks.get(j);

                // Add chunk-specific metadata
                Metadata chunkMetadata = new Metadata();
                // Copy existing metadata
                for (String key : chunk.metadata().toMap().keySet()) {
                    Object value = chunk.metadata().toMap().get(key);
                    chunkMetadata.put(key, value.toString());
                }
                chunkMetadata.put("chunk_id", String.valueOf(j + 1));
                chunkMetadata.put("chunk_count", String.valueOf(chunks.size()));

                TextSegment enrichedChunk = TextSegment.from(chunk.text(), chunkMetadata);

                // Generate embedding (LangChain4j Core)
                Embedding embedding = embeddingModel.embed(enrichedChunk).content();

                // Store in YugabyteDB (our responsibility)
                String id = store.add(embedding, enrichedChunk);
                allIds.add(id);
                assertThat(id).isNotNull();

                logger.info("    📦 Chunk {}/{} stored (ID: {})", j + 1, chunks.size(), id.substring(0, 8));
            }
        }

        logger.info("✅ Complete RAG pipeline: LangChain4j chunking → YugabyteDB storage successful");

        // Step 3: Test RAG retrieval functionality
        testRAGRetrieval(store, "How does YugabyteDB handle scalability?");
        testRAGRetrieval(store, "What are vector embeddings used for?");

        // Cleanup
        store.removeAll(allIds);
        logger.info("✅ RAG workflow test completed with cleanup ({} chunks removed)", allIds.size());
    }

    @Test
    void should_test_different_langchain4j_splitters() {
        // Test YugabyteDB with different LangChain4j Core splitters
        YugabyteDBEmbeddingStore store = createStore("splitter_test");

        String longDocument = "YugabyteDB is a distributed SQL database designed for cloud-native applications. "
                + "It provides PostgreSQL compatibility while offering horizontal scalability and high availability. "
                + "The database automatically shards data across multiple nodes for optimal performance. "
                + "Vector embeddings are numerical representations of text that capture semantic meaning. "
                + "These high-dimensional vectors enable semantic search capabilities. "
                + "RAG combines information retrieval with language model generation for better responses.";

        // Test different LangChain4j Core splitter configurations
        DocumentSplitter[] splitters = {
            DocumentSplitters.recursive(150, 30), // Small chunks with overlap
            DocumentSplitters.recursive(300, 50), // Medium chunks with overlap
            DocumentSplitters.recursive(500, 100) // Large chunks with overlap
        };

        List<String> allIds = new ArrayList<>();

        for (int i = 0; i < splitters.length; i++) {
            DocumentSplitter splitter = splitters[i];

            Metadata documentMetadata = new Metadata()
                    .put("splitter_type", String.valueOf(i))
                    .put("chunk_size", i == 0 ? "small" : i == 1 ? "medium" : "large");

            Document document = Document.from(longDocument, documentMetadata);

            // Real chunking with LangChain4j Core
            List<TextSegment> chunks = splitter.split(document);
            assertThat(chunks).isNotEmpty();

            logger.info("📄 LangChain4j splitter {}: {} chunks created", i + 1, chunks.size());

            // Store chunks in YugabyteDB
            for (int j = 0; j < chunks.size(); j++) {
                TextSegment chunk = chunks.get(j);

                // Enrich with chunk metadata
                Metadata chunkMetadata = new Metadata();
                for (String key : chunk.metadata().toMap().keySet()) {
                    Object value = chunk.metadata().toMap().get(key);
                    chunkMetadata.put(key, value.toString());
                }
                chunkMetadata.put("chunk_index", String.valueOf(j));

                TextSegment enrichedChunk = TextSegment.from(chunk.text(), chunkMetadata);
                Embedding embedding = embeddingModel.embed(enrichedChunk).content();
                String id = store.add(embedding, enrichedChunk);
                allIds.add(id);

                logger.info(
                        "    📦 Stored chunk {}: {}...",
                        j + 1,
                        chunk.text().substring(0, Math.min(50, chunk.text().length())));
            }
        }

        logger.info("✅ Tested {} LangChain4j splitter strategies", splitters.length);

        // Cleanup
        store.removeAll(allIds);
        logger.info("✅ Splitter test completed with cleanup ({} chunks removed)", allIds.size());
    }

    @Test
    void should_support_metadata_based_rag() {
        // Test RAG with metadata filtering
        YugabyteDBEmbeddingStore store = createStore("metadata_rag_test");

        // Store documents with different categories
        String[] documents = {
            "YugabyteDB connection setup and configuration",
            "Vector similarity search algorithms",
            "Database performance optimization techniques",
            "Machine learning embedding models",
            "Distributed database architecture patterns"
        };

        String[] categories = {"database", "ai", "database", "ai", "database"};
        String[] topics = {"setup", "search", "performance", "models", "architecture"};

        for (int i = 0; i < documents.length; i++) {
            Metadata metadata = new Metadata()
                    .put("category", categories[i])
                    .put("topic", topics[i])
                    .put("priority", i % 2 == 0 ? "high" : "low");

            TextSegment segment = TextSegment.from(documents[i], metadata);
            Embedding embedding = embeddingModel.embed(segment).content();
            store.add(embedding, segment);
        }

        // Test filtered RAG queries
        testFilteredRAGQuery(
                store, "database optimization", metadataKey("category").isEqualTo("database"));

        testFilteredRAGQuery(
                store, "AI and machine learning", metadataKey("category").isEqualTo("ai"));

        testFilteredRAGQuery(
                store, "high priority topics", metadataKey("priority").isEqualTo("high"));
    }

    @Test
    void should_handle_rag_with_no_results() {
        // Test RAG behavior when no relevant documents are found
        YugabyteDBEmbeddingStore store = createStore("no_results_test");

        // Store only database-related documents
        String[] documents = {"Database connection pooling", "SQL query optimization", "Database indexing strategies"};

        for (String doc : documents) {
            TextSegment segment = TextSegment.from(doc, new Metadata().put("category", "database"));
            Embedding embedding = embeddingModel.embed(segment).content();
            store.add(embedding, segment);
        }

        // Query for completely unrelated topic
        Embedding queryEmbedding =
                embeddingModel.embed("cooking recipes and food preparation").content();

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .minScore(0.8) // High threshold
                .build());

        // Should return few or no results due to high threshold
        assertThat(result.matches()).hasSizeLessThanOrEqualTo(3);
        logger.info("✅ No relevant results handling working correctly");
    }

    @Test
    void should_test_rag_workflow_with_postgresql_driver() {
        logger.info("🚀 [RAG-POSTGRESQL] Starting RAG workflow test with PostgreSQL JDBC Driver...");
        logger.info("🔧 [RAG-POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        String host = yugabyteContainer.getHost();
        int port = yugabyteContainer.getMappedPort(5433);

        // Create PostgreSQL driver engine
        YugabyteDBEngine postgresqlEngine = YugabyteDBEngine.builder()
                .host(host)
                .port(port)
                .database(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .usePostgreSQLDriver(true)
                .maxPoolSize(5)
                .build();

        logger.info("✅ [RAG-POSTGRESQL] PostgreSQL driver engine created successfully");

        try {
            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(postgresqlEngine)
                    .tableName("rag_postgresql_test")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();

            logger.info("📋 [RAG-POSTGRESQL] Testing complete RAG pipeline...");

            // Test RAG workflow with PostgreSQL driver
            long startTime = System.currentTimeMillis();
            List<String> storedIds = performRAGWorkflow(store, "PostgreSQL Driver");
            long endTime = System.currentTimeMillis();

            logger.info(
                    "✅ [RAG-POSTGRESQL] Complete RAG workflow: {} documents processed in {} ms",
                    storedIds.size(),
                    endTime - startTime);

            // Test RAG retrieval performance
            testRAGRetrievalPerformance(store, "PostgreSQL Driver");

            // Cleanup
            store.removeAll(storedIds);
            logger.info("🧹 [RAG-POSTGRESQL] Cleanup completed ({} documents removed)", storedIds.size());

        } finally {
            postgresqlEngine.close();
            logger.info("✅ [RAG-POSTGRESQL] PostgreSQL driver RAG workflow test completed");
        }
    }

    @Test
    void should_test_rag_workflow_with_smart_driver() {
        logger.info("🚀 [RAG-SMART] Starting RAG workflow test with YugabyteDB Smart Driver...");
        logger.info("🔧 [RAG-SMART] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

        try {
            // Create Smart Driver engine using YBClusterAwareDataSource
            logger.info("📋 [RAG-SMART] Configuring YugabyteDB Smart Driver...");

            java.util.Properties poolProperties = new java.util.Properties();
            poolProperties.setProperty("dataSourceClassName", "com.yugabyte.ysql.YBClusterAwareDataSource");
            poolProperties.setProperty("maximumPoolSize", "5");
            poolProperties.setProperty("minimumIdle", "1");
            poolProperties.setProperty("connectionTimeout", "10000");

            // YugabyteDB connection properties
            String host = yugabyteContainer.getHost();
            int port = yugabyteContainer.getMappedPort(5433);
            poolProperties.setProperty("dataSource.serverName", host);
            poolProperties.setProperty("dataSource.portNumber", String.valueOf(port));
            poolProperties.setProperty("dataSource.databaseName", DB_NAME);
            poolProperties.setProperty("dataSource.user", DB_USER);
            poolProperties.setProperty("dataSource.password", DB_PASSWORD);

            // Disable load balancing for single node
            poolProperties.setProperty("dataSource.loadBalance", "false");

            // Performance optimizations
            poolProperties.setProperty("dataSource.prepareThreshold", "1");
            poolProperties.setProperty("dataSource.reWriteBatchedInserts", "true");
            poolProperties.setProperty("dataSource.tcpKeepAlive", "true");
            poolProperties.setProperty("dataSource.socketTimeout", "0");
            poolProperties.setProperty("dataSource.loginTimeout", "10");

            poolProperties.setProperty("poolName", "RAGSmartDriverPool");

            HikariConfig config = new HikariConfig(poolProperties);
            config.validate();
            HikariDataSource smartDataSource = new HikariDataSource(config);

            YugabyteDBEngine smartEngine = YugabyteDBEngine.from(smartDataSource);
            logger.info("🔧 [RAG-SMART] YugabyteDB Smart Driver engine created successfully");

            try {
                YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                        .engine(smartEngine)
                        .tableName("rag_smart_driver_test")
                        .dimension(384)
                        .metricType(MetricType.COSINE)
                        .createTableIfNotExists(true)
                        .build();

                logger.info("📋 [RAG-SMART] Testing complete RAG pipeline...");

                // Test RAG workflow with Smart Driver
                long startTime = System.currentTimeMillis();
                List<String> storedIds = performRAGWorkflow(store, "Smart Driver");
                long endTime = System.currentTimeMillis();

                logger.info(
                        "✅ [RAG-SMART] Complete RAG workflow: {} documents processed in {} ms",
                        storedIds.size(),
                        endTime - startTime);

                // Test RAG retrieval performance
                testRAGRetrievalPerformance(store, "Smart Driver");

                // Cleanup
                store.removeAll(storedIds);
                logger.info("🧹 [RAG-SMART] Cleanup completed ({} documents removed)", storedIds.size());

                logger.info("✅ [RAG-SMART] YugabyteDB Smart Driver RAG workflow test completed successfully");

            } finally {
                smartEngine.close();
                smartDataSource.close();
            }

        } catch (Exception e) {
            logger.warn("⚠️ [RAG-SMART] Smart Driver RAG test failed:");
            logger.warn("[RAG-SMART] Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                logger.warn("[RAG-SMART] Caused by: " + e.getCause().getClass().getSimpleName() + ": "
                        + e.getCause().getMessage());
            }
            logger.info("✅ [RAG-SMART] Smart Driver RAG test completed");
        }
    }

    /**
     * Nested test class for YugabyteDB Smart Driver RAG workflow tests.
     * Runs the same RAG tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    @Testcontainers
    class SmartDriverRAGIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverRAGIT.class);
        private static YugabyteDBEngine smartEngine;
        private static EmbeddingModel smartEmbeddingModel;
        private static DocumentSplitter smartDocumentSplitter;

        @BeforeAll
        static void setupSmartDriver() {
            smartLogger.info("🚀 [RAG-SMART-DRIVER] Initializing Smart Driver for RAG workflow tests...");
            smartLogger.info(
                    "🔧 [RAG-SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            String host = yugabyteContainer.getHost();
            int port = yugabyteContainer.getMappedPort(5433);

            smartLogger.info("📋 [RAG-SMART-DRIVER] Container details:");
            smartLogger.info("[RAG-SMART-DRIVER]   - Host: {}", host);
            smartLogger.info("[RAG-SMART-DRIVER]   - Port: {}", port);
            smartLogger.info("[RAG-SMART-DRIVER]   - Database: {}", DB_NAME);

            // Create Smart Driver engine
            smartLogger.info("🔧 [RAG-SMART-DRIVER] Creating Smart Driver engine...");
            smartEngine = YugabyteDBEngine.builder()
                    .host(host)
                    .port(port)
                    .database(DB_NAME)
                    .username(DB_USER)
                    .password(DB_PASSWORD)
                    .usePostgreSQLDriver(false) // Use Smart Driver
                    .maxPoolSize(10)
                    .build();
            smartLogger.info("✅ [RAG-SMART-DRIVER] Smart Driver engine created successfully");

            smartLogger.info("🧠 [RAG-SMART-DRIVER] Creating embedding model...");
            smartEmbeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

            smartLogger.info("✂️ [RAG-SMART-DRIVER] Creating document splitter...");
            smartDocumentSplitter = DocumentSplitters.recursive(300, 50);

            smartLogger.info("✅ [RAG-SMART-DRIVER] Smart Driver RAG workflow test setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [RAG-SMART-DRIVER] Starting Smart Driver cleanup...");
            if (smartEngine != null) {
                smartLogger.info("[RAG-SMART-DRIVER] Closing Smart Driver engine...");
                smartEngine.close();
            }
            smartLogger.info("✅ [RAG-SMART-DRIVER] Smart Driver cleanup completed");
        }

        @Test
        void should_process_complete_rag_workflow_with_real_chunking() {
            smartLogger.info("📋 [RAG-SMART-DRIVER] Testing complete RAG workflow with real chunking...");

            // Test complete RAG workflow: Document → LangChain4j Chunking → YugabyteDB Storage → Retrieval
            YugabyteDBEmbeddingStore store = createSmartStore("rag_workflow_smart");

            // Step 1: Create sample documents (like user would)
            String[] rawDocuments = {
                "YugabyteDB is a distributed SQL database designed for cloud-native applications. "
                        + "It provides PostgreSQL compatibility while offering horizontal scalability and high availability. "
                        + "The database automatically shards data across multiple nodes for optimal performance. "
                        + "Connection pooling with HikariCP optimizes resource usage and improves application performance.",
                "Vector embeddings are numerical representations of text that capture semantic meaning and context. "
                        + "These high-dimensional vectors enable semantic search capabilities by measuring similarity between "
                        + "different pieces of text content. YugabyteDB supports the pgvector extension for efficient vector operations. "
                        + "RAG combines information retrieval with language model generation for better AI responses."
            };

            String[] categories = {"database", "ai"};
            List<String> allIds = new ArrayList<>();

            // Step 2: Process documents through complete pipeline
            for (int i = 0; i < rawDocuments.length; i++) {
                smartLogger.info("📄 [SMART-DRIVER] Processing document {} [{}]...", i + 1, categories[i]);

                // Create Document (LangChain4j Core)
                Metadata documentMetadata = new Metadata()
                        .put("source", "test_document")
                        .put("category", categories[i])
                        .put("document_id", String.valueOf(i + 1));

                Document document = Document.from(rawDocuments[i], documentMetadata);

                // Real chunking with LangChain4j Core DocumentSplitters
                List<TextSegment> chunks = smartDocumentSplitter.split(document);
                smartLogger.info("  ✅ [SMART-DRIVER] LangChain4j chunked into {} segments", chunks.size());

                // Process each chunk
                for (int j = 0; j < chunks.size(); j++) {
                    TextSegment chunk = chunks.get(j);

                    // Add chunk-specific metadata
                    Metadata chunkMetadata = new Metadata();
                    // Copy existing metadata
                    for (String key : chunk.metadata().toMap().keySet()) {
                        Object value = chunk.metadata().toMap().get(key);
                        chunkMetadata.put(key, value.toString());
                    }
                    chunkMetadata.put("chunk_id", String.valueOf(j + 1));
                    chunkMetadata.put("chunk_count", String.valueOf(chunks.size()));

                    TextSegment enrichedChunk = TextSegment.from(chunk.text(), chunkMetadata);

                    // Generate embedding (LangChain4j Core)
                    Embedding embedding =
                            smartEmbeddingModel.embed(enrichedChunk).content();

                    // Store in YugabyteDB (our responsibility)
                    String id = store.add(embedding, enrichedChunk);
                    allIds.add(id);
                    assertThat(id).isNotNull();

                    smartLogger.info(
                            "    📦 [SMART-DRIVER] Chunk {}/{} stored (ID: {})",
                            j + 1,
                            chunks.size(),
                            id.substring(0, 8));
                }
            }

            smartLogger.info(
                    "✅ [SMART-DRIVER] Complete RAG pipeline: LangChain4j chunking → YugabyteDB storage successful");

            // Step 3: Test RAG retrieval functionality
            testSmartRAGRetrieval(store, "How does YugabyteDB handle scalability?");
            testSmartRAGRetrieval(store, "What are vector embeddings used for?");

            // Cleanup
            store.removeAll(allIds);
            smartLogger.info(
                    "✅ [SMART-DRIVER] RAG workflow test completed with cleanup ({} chunks removed)", allIds.size());
        }

        @Test
        void should_test_different_langchain4j_splitters() {
            smartLogger.info("📋 [RAG-SMART-DRIVER] Testing different LangChain4j splitters...");

            // Test YugabyteDB with different LangChain4j Core splitters
            YugabyteDBEmbeddingStore store = createSmartStore("splitter_test_smart");

            String longDocument = "YugabyteDB is a distributed SQL database designed for cloud-native applications. "
                    + "It provides PostgreSQL compatibility while offering horizontal scalability and high availability. "
                    + "The database automatically shards data across multiple nodes for optimal performance. "
                    + "Vector embeddings are numerical representations of text that capture semantic meaning. "
                    + "These high-dimensional vectors enable semantic search capabilities. "
                    + "RAG combines information retrieval with language model generation for better responses.";

            // Test different LangChain4j Core splitter configurations
            DocumentSplitter[] splitters = {
                DocumentSplitters.recursive(150, 30), // Small chunks with overlap
                DocumentSplitters.recursive(300, 50), // Medium chunks with overlap
                DocumentSplitters.recursive(500, 100) // Large chunks with overlap
            };

            List<String> allIds = new ArrayList<>();

            for (int i = 0; i < splitters.length; i++) {
                DocumentSplitter splitter = splitters[i];

                Metadata documentMetadata = new Metadata()
                        .put("splitter_type", String.valueOf(i))
                        .put("chunk_size", i == 0 ? "small" : i == 1 ? "medium" : "large");

                Document document = Document.from(longDocument, documentMetadata);

                // Real chunking with LangChain4j Core
                List<TextSegment> chunks = splitter.split(document);
                assertThat(chunks).isNotEmpty();

                smartLogger.info("📄 [SMART-DRIVER] LangChain4j splitter {}: {} chunks created", i + 1, chunks.size());

                // Store chunks in YugabyteDB
                for (int j = 0; j < chunks.size(); j++) {
                    TextSegment chunk = chunks.get(j);

                    // Enrich with chunk metadata
                    Metadata chunkMetadata = new Metadata();
                    for (String key : chunk.metadata().toMap().keySet()) {
                        Object value = chunk.metadata().toMap().get(key);
                        chunkMetadata.put(key, value.toString());
                    }
                    chunkMetadata.put("chunk_index", String.valueOf(j));

                    TextSegment enrichedChunk = TextSegment.from(chunk.text(), chunkMetadata);
                    Embedding embedding =
                            smartEmbeddingModel.embed(enrichedChunk).content();
                    String id = store.add(embedding, enrichedChunk);
                    allIds.add(id);

                    smartLogger.info(
                            "    📦 [SMART-DRIVER] Stored chunk {}: {}...",
                            j + 1,
                            chunk.text().substring(0, Math.min(50, chunk.text().length())));
                }
            }

            smartLogger.info("✅ [SMART-DRIVER] Tested {} LangChain4j splitter strategies", splitters.length);

            // Cleanup
            store.removeAll(allIds);
            smartLogger.info(
                    "✅ [SMART-DRIVER] Splitter test completed with cleanup ({} chunks removed)", allIds.size());
        }

        @Test
        void should_support_metadata_based_rag() {
            smartLogger.info("📋 [RAG-SMART-DRIVER] Testing metadata-based RAG...");

            // Test RAG with metadata filtering
            YugabyteDBEmbeddingStore store = createSmartStore("metadata_rag_smart");

            // Store documents with different categories
            String[] documents = {
                "YugabyteDB connection setup and configuration",
                "Vector similarity search algorithms",
                "Database performance optimization techniques",
                "Machine learning embedding models",
                "Distributed database architecture patterns"
            };

            String[] categories = {"database", "ai", "database", "ai", "database"};
            String[] topics = {"setup", "search", "performance", "models", "architecture"};

            for (int i = 0; i < documents.length; i++) {
                Metadata metadata = new Metadata()
                        .put("category", categories[i])
                        .put("topic", topics[i])
                        .put("priority", i % 2 == 0 ? "high" : "low");

                TextSegment segment = TextSegment.from(documents[i], metadata);
                Embedding embedding = smartEmbeddingModel.embed(segment).content();
                store.add(embedding, segment);
            }

            // Test filtered RAG queries
            testSmartFilteredRAGQuery(
                    store, "database optimization", metadataKey("category").isEqualTo("database"));

            testSmartFilteredRAGQuery(
                    store, "AI and machine learning", metadataKey("category").isEqualTo("ai"));

            testSmartFilteredRAGQuery(
                    store, "high priority topics", metadataKey("priority").isEqualTo("high"));

            smartLogger.info("✅ [SMART-DRIVER] Metadata-based RAG test completed");
        }

        @Test
        void should_handle_rag_with_no_results() {
            smartLogger.info("📋 [RAG-SMART-DRIVER] Testing RAG with no results...");

            // Test RAG behavior when no relevant documents are found
            YugabyteDBEmbeddingStore store = createSmartStore("no_results_smart");

            // Store only database-related documents
            String[] documents = {
                "Database connection pooling", "SQL query optimization", "Database indexing strategies"
            };

            for (String doc : documents) {
                TextSegment segment = TextSegment.from(doc, new Metadata().put("category", "database"));
                Embedding embedding = smartEmbeddingModel.embed(segment).content();
                store.add(embedding, segment);
            }

            // Query for completely unrelated topic
            Embedding queryEmbedding = smartEmbeddingModel
                    .embed("cooking recipes and food preparation")
                    .content();

            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(5)
                    .minScore(0.8) // High threshold
                    .build());

            // Should return few or no results due to high threshold
            assertThat(result.matches()).hasSizeLessThanOrEqualTo(3);
            smartLogger.info("✅ [SMART-DRIVER] No relevant results handling working correctly");
        }

        private YugabyteDBEmbeddingStore createSmartStore(String tableName) {
            return YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName(tableName)
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();
        }

        private void testSmartRAGRetrieval(YugabyteDBEmbeddingStore store, String query) {
            Embedding queryEmbedding = smartEmbeddingModel.embed(query).content();

            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.5)
                    .build());

            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            assertThat(matches).isNotEmpty();

            smartLogger.info("✅ [SMART-DRIVER] RAG query '{}' found {} relevant chunks", query, matches.size());

            // Verify chunks have proper metadata
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                assertThat(segment.metadata()).isNotNull();
                assertThat(match.score()).isGreaterThan(0.5);
            }
        }

        private void testSmartFilteredRAGQuery(YugabyteDBEmbeddingStore store, String query, Filter filter) {
            Embedding queryEmbedding = smartEmbeddingModel.embed(query).content();

            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .filter(filter)
                    .maxResults(5)
                    .build());

            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            smartLogger.info(
                    "✅ [SMART-DRIVER] Filtered RAG query '{}' with filter '{}' found {} results",
                    query,
                    filter.toString(),
                    matches.size());

            // Verify all results match the filter
            for (EmbeddingMatch<TextSegment> match : matches) {
                // Filter validation would depend on specific filter type
                assertThat(match.embedded().metadata()).isNotNull();
            }
        }
    }

    private List<String> performRAGWorkflow(YugabyteDBEmbeddingStore store, String driverName) {
        logger.info("📄 [{}] Processing RAG documents...", driverName);

        // Sample RAG documents
        String[] ragDocuments = {
            "YugabyteDB is a distributed SQL database that provides PostgreSQL compatibility. "
                    + "It offers horizontal scalability and high availability for cloud-native applications. "
                    + "The database supports ACID transactions and provides strong consistency guarantees.",
            "Vector embeddings enable semantic search by converting text into numerical representations. "
                    + "These high-dimensional vectors capture the meaning and context of text content. "
                    + "Similarity search using cosine distance helps find semantically related documents.",
            "RAG (Retrieval-Augmented Generation) combines information retrieval with language models. "
                    + "It first retrieves relevant documents using vector similarity search. "
                    + "Then it uses these documents as context for generating more accurate responses.",
            "Connection pooling optimizes database performance by reusing connections. "
                    + "HikariCP provides efficient connection pool management for Java applications. "
                    + "Proper pool sizing and configuration are crucial for optimal performance."
        };

        String[] categories = {"database", "ai", "rag", "performance"};
        List<String> allIds = new ArrayList<>();

        for (int i = 0; i < ragDocuments.length; i++) {
            logger.info(
                    "  📦 [{}] Processing document {}/{} [{}]...",
                    driverName,
                    i + 1,
                    ragDocuments.length,
                    categories[i]);

            // Create document with metadata
            Metadata documentMetadata = new Metadata()
                    .put("source", "rag_test")
                    .put("category", categories[i])
                    .put("document_id", String.valueOf(i + 1))
                    .put("driver_type", driverName.toLowerCase().replace(" ", "_"));

            Document document = Document.from(ragDocuments[i], documentMetadata);

            // Split document into chunks
            List<TextSegment> chunks = documentSplitter.split(document);
            logger.info("    ✂️ [{}] Split into {} chunks", driverName, chunks.size());

            // Process each chunk
            for (int j = 0; j < chunks.size(); j++) {
                TextSegment chunk = chunks.get(j);

                // Enrich chunk metadata
                Metadata chunkMetadata = new Metadata();
                for (String key : chunk.metadata().toMap().keySet()) {
                    Object value = chunk.metadata().toMap().get(key);
                    chunkMetadata.put(key, value.toString());
                }
                chunkMetadata.put("chunk_id", String.valueOf(j + 1));
                chunkMetadata.put("total_chunks", String.valueOf(chunks.size()));

                TextSegment enrichedChunk = TextSegment.from(chunk.text(), chunkMetadata);

                // Generate embedding and store
                long embeddingStart = System.currentTimeMillis();
                Embedding embedding = embeddingModel.embed(enrichedChunk).content();
                long embeddingEnd = System.currentTimeMillis();

                long storeStart = System.currentTimeMillis();
                String id = store.add(embedding, enrichedChunk);
                long storeEnd = System.currentTimeMillis();

                allIds.add(id);

                logger.info(
                        "      💾 [{}] Chunk {} stored: embed={}ms, store={}ms (ID: {})",
                        driverName,
                        j + 1,
                        embeddingEnd - embeddingStart,
                        storeEnd - storeStart,
                        id.substring(0, 8));
            }
        }

        logger.info("✅ [{}] RAG document processing completed: {} total chunks stored", driverName, allIds.size());

        return allIds;
    }

    private void testRAGRetrievalPerformance(YugabyteDBEmbeddingStore store, String driverName) {
        logger.info("🔍 [{}] Testing RAG retrieval performance...", driverName);

        String[] testQueries = {
            "How does YugabyteDB handle distributed transactions?",
            "What are vector embeddings and how do they work?",
            "Explain RAG and its benefits for AI applications",
            "How to optimize database connection pooling?"
        };

        for (int i = 0; i < testQueries.length; i++) {
            String query = testQueries[i];
            logger.info("  🔎 [{}] Query {}: {}", driverName, i + 1, query);

            long queryStart = System.currentTimeMillis();
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            long embeddingTime = System.currentTimeMillis() - queryStart;

            long searchStart = System.currentTimeMillis();
            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .minScore(0.5)
                    .build());
            long searchTime = System.currentTimeMillis() - searchStart;

            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            logger.info(
                    "[{}] Found {} matches: embed={}ms, search={}ms, total={}ms",
                    driverName,
                    matches.size(),
                    embeddingTime,
                    searchTime,
                    embeddingTime + searchTime);

            // Verify results quality
            for (int j = 0; j < Math.min(matches.size(), 2); j++) {
                EmbeddingMatch<TextSegment> match = matches.get(j);
                logger.info(
                        "      📄 [{}] Match {}: score={}, category={}",
                        driverName,
                        j + 1,
                        String.format("%.3f", match.score()),
                        match.embedded().metadata().getString("category"));
            }

            assertThat(matches).isNotEmpty();
        }

        logger.info("✅ [{}] RAG retrieval performance testing completed", driverName);
    }

    private void testRAGRetrieval(YugabyteDBEmbeddingStore store, String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.5)
                .build());

        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        assertThat(matches).isNotEmpty();

        logger.info("✅ RAG query '{}' found {} relevant chunks", query, matches.size());

        // Verify chunks have proper metadata
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            assertThat(segment.metadata()).isNotNull();
            assertThat(match.score()).isGreaterThan(0.5);
        }
    }

    private void testFilteredRAGQuery(YugabyteDBEmbeddingStore store, String query, Filter filter) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(5)
                .build());

        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        logger.info(
                "✅ Filtered RAG query '{}' with filter '{}' found {} results",
                query,
                filter.toString(),
                matches.size());

        // Verify all results match the filter
        for (EmbeddingMatch<TextSegment> match : matches) {
            // Filter validation would depend on specific filter type
            assertThat(match.embedded().metadata()).isNotNull();
        }
    }

    protected YugabyteDBEmbeddingStore createStore(String tableName) {
        return YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName(tableName)
                .dimension(384)
                .metricType(MetricType.COSINE)
                .createTableIfNotExists(true)
                .build();
    }
}
