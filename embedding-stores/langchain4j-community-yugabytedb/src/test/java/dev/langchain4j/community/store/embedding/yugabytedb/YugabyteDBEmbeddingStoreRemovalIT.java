package dev.langchain4j.community.store.embedding.yugabytedb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for YugabyteDBEmbeddingStore removal operations.
 * Tests both PostgreSQL JDBC driver (recommended) and YugabyteDB Smart Driver.
 * Each driver runs the full suite of removal tests from EmbeddingStoreWithRemovalIT.
 */
@Testcontainers
class YugabyteDBEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBEmbeddingStoreRemovalIT.class);

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> yugabyteContainer = new GenericContainer<>(
                    DockerImageName.parse("yugabytedb/yugabyte:2025.1.0.1-b3"))
            .withExposedPorts(5433, 7000, 9000, 15433, 9042)
            .withCommand("bin/yugabyted", "start", "--background=false")
            .waitingFor(Wait.forListeningPorts(5433).withStartupTimeout(Duration.ofMinutes(5)));

    static YugabyteDBEngine engine;
    static HikariDataSource dataSource;
    static EmbeddingModel embeddingModel;
    static YugabyteDBEmbeddingStore store;

    // TestContainers connection details
    private static final String DB_NAME = "yugabyte";
    private static final String DB_USER = "yugabyte";
    private static final String DB_PASSWORD = "yugabyte";

    @BeforeAll
    static void setup() throws Exception {
        logger.info("🚀 [POSTGRESQL] Initializing PostgreSQL JDBC driver for removal tests...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        logger.info("🧠 [POSTGRESQL] Creating embedding model (AllMiniLmL6V2QuantizedEmbeddingModel)...");
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        logger.info("✅ [POSTGRESQL] Embedding model created successfully");

        logger.info("📋 [POSTGRESQL] Container details:");
        logger.info("[POSTGRESQL]   - Image: yugabytedb/yugabyte:2025.1.0.1-b3");
        logger.info("[POSTGRESQL]   - Host: {}", yugabyteContainer.getHost());
        logger.info("[POSTGRESQL]   - Mapped port: {}", yugabyteContainer.getMappedPort(5433));
        logger.info("[POSTGRESQL]   - Container ID: {}", yugabyteContainer.getContainerId());

        // Wait for YugabyteDB to be ready
        logger.info("⏳ [POSTGRESQL] Waiting for YugabyteDB to fully initialize...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Create PostgreSQL driver engine (recommended)
        logger.info("🔧 [POSTGRESQL] Creating YugabyteDBEngine with PostgreSQL driver...");
        logger.info(
                "🔧 [POSTGRESQL] Connection URL: jdbc:postgresql://{}:{}/{}",
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
        logger.info("✅ [POSTGRESQL] YugabyteDBEngine created successfully");

        // Enable pgvector extension using JDBC
        logger.info("🔧 [POSTGRESQL] Enabling pgvector extension via JDBC...");
        try (var connection = engine.getConnection();
                var stmt = connection.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");
            logger.info("✅ [POSTGRESQL] pgvector extension enabled successfully");
        } catch (Exception e) {
            logger.warn("⚠️ [POSTGRESQL] Warning: Failed to enable pgvector extension: {}", e.getMessage());
        }

        // Create and cache the embedding store (avoid recreating on every call)
        logger.info("🔧 [POSTGRESQL] Creating embedding store instance...");
        store = YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_removal_embeddings")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .createTableIfNotExists(true)
                .build();
        logger.info("✅ [POSTGRESQL] Embedding store created and cached");

        logger.info("🎉 [POSTGRESQL] PostgreSQL driver setup completed successfully");
    }

    @AfterAll
    static void cleanup() {
        logger.info("🧹 [POSTGRESQL] Starting PostgreSQL driver cleanup...");

        if (engine != null) {
            logger.info("[POSTGRESQL] Closing YugabyteDBEngine...");
            engine.close();
        }
        if (dataSource != null) {
            logger.info("[POSTGRESQL] Closing HikariDataSource...");
            dataSource.close();
        }

        logger.info("✅ [POSTGRESQL] PostgreSQL driver cleanup completed successfully");
    }

    @BeforeEach
    void logTestStart(TestInfo testInfo) {
        logger.info("▶️ [POSTGRESQL] Starting test: {}", testInfo.getDisplayName());
        // Clear store before each test to ensure isolation
        try {
            store.removeAll();
            logger.info("[POSTGRESQL] Store cleared for test: {}", testInfo.getDisplayName());
        } catch (Exception e) {
            logger.warn("[POSTGRESQL] Failed to clear store: {}", e.getMessage());
        }
    }

    @AfterEach
    void logTestEnd(TestInfo testInfo) {
        logger.info("✅ [POSTGRESQL] Completed test: {}", testInfo.getDisplayName());
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        // Return cached store instance instead of creating new one each time
        return store;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * Nested test class for YugabyteDB Smart Driver removal tests.
     * Runs all removal tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    @Testcontainers
    class SmartDriverRemovalIT extends EmbeddingStoreWithRemovalIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverRemovalIT.class);
        private static YugabyteDBEngine smartEngine;
        private static YugabyteDBEmbeddingStore smartStore;

        @BeforeAll
        static void setupSmartDriver() throws Exception {
            smartLogger.info("🚀 [SMART-DRIVER-SETUP] Initializing Smart Driver for removal tests...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER-SETUP] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            smartLogger.info("📋 [SMART-DRIVER-SETUP] Container details:");
            smartLogger.info("[SMART-DRIVER-SETUP]   - Host: {}", yugabyteContainer.getHost());
            smartLogger.info("[SMART-DRIVER-SETUP]   - Mapped port: {}", yugabyteContainer.getMappedPort(5433));

            // Create Smart Driver engine
            smartLogger.info("🔧 [SMART-DRIVER-SETUP] Creating Smart Driver engine...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER-SETUP] Connection URL: jdbc:yugabytedb://{}:{}/{}",
                    yugabyteContainer.getHost(),
                    yugabyteContainer.getMappedPort(5433),
                    DB_NAME);

            smartEngine = YugabyteDBEngine.builder()
                    .host(yugabyteContainer.getHost())
                    .port(yugabyteContainer.getMappedPort(5433))
                    .database(DB_NAME)
                    .username(DB_USER)
                    .password(DB_PASSWORD)
                    .usePostgreSQLDriver(false) // Use Smart Driver
                    .maxPoolSize(10)
                    .build();
            smartLogger.info("✅ [SMART-DRIVER-SETUP] Smart Driver engine created successfully");

            // Create and cache the embedding store (avoid recreating on every call)
            smartLogger.info("🔧 [SMART-DRIVER-SETUP] Creating embedding store instance...");
            smartStore = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("test_removal_smart")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();
            smartLogger.info("✅ [SMART-DRIVER-SETUP] Embedding store created and cached");

            smartLogger.info("🎉 [SMART-DRIVER-SETUP] Smart Driver setup completed successfully");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER-CLEANUP] Starting Smart Driver cleanup...");

            if (smartEngine != null) {
                smartLogger.info("[SMART-DRIVER-CLEANUP] Closing Smart Driver engine...");
                smartEngine.close();
            }

            smartLogger.info("✅ [SMART-DRIVER-CLEANUP] Smart Driver cleanup completed");
        }

        @BeforeEach
        void logTestStart(TestInfo testInfo) {
            smartLogger.info("▶️ [SMART-DRIVER] Starting test: {}", testInfo.getDisplayName());
            // Clear store before each test to ensure isolation
            try {
                smartStore.removeAll();
                smartLogger.info("[SMART-DRIVER] Store cleared for test: {}", testInfo.getDisplayName());
            } catch (Exception e) {
                smartLogger.warn("[SMART-DRIVER] Failed to clear store: {}", e.getMessage());
            }
        }

        @AfterEach
        void logTestEnd(TestInfo testInfo) {
            smartLogger.info("✅ [SMART-DRIVER] Completed test: {}", testInfo.getDisplayName());
        }

        @Override
        protected EmbeddingStore<TextSegment> embeddingStore() {
            // Return cached store instance instead of creating new one each time
            return smartStore;
        }

        @Override
        protected EmbeddingModel embeddingModel() {
            return embeddingModel;
        }
    }
}
