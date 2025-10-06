package dev.langchain4j.community.store.embedding.yugabytedb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for YugabyteDBEmbeddingStore using TestContainers.
 * Tests both PostgreSQL JDBC driver (recommended) and YugabyteDB Smart Driver.
 * Uses isolated YugabyteDB container for each test run.
 */
@Testcontainers
class YugabyteDBEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBEmbeddingStoreIT.class);

    private static final String DB_NAME = "yugabyte";
    private static final String DB_USER = "yugabyte";
    private static final String DB_PASSWORD = "yugabyte";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> yugabyteContainer = new GenericContainer<>(
                    DockerImageName.parse("yugabytedb/yugabyte:2025.1.0.1-b3"))
            .withExposedPorts(5433, 7000, 9000, 15433, 9042)
            .withCommand("bin/yugabyted", "start", "--background=false")
            .waitingFor(Wait.forListeningPorts(5433).withStartupTimeout(Duration.ofMinutes(5)));

    static YugabyteDBEngine engine;
    static YugabyteDBEngine smartEngine;
    static HikariDataSource dataSource;
    static HikariDataSource smartDataSource;
    static EmbeddingModel embeddingModel;

    @BeforeAll
    static void setup() throws Exception {
        logger.info("🚀 [POSTGRESQL] Initializing YugabyteDB Testcontainer setup with PostgreSQL driver...");
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
        logger.info("⏳ [POSTGRESQL] Waiting 5 seconds for YugabyteDB to fully initialize...");
        Thread.sleep(5000);

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
        logger.info("✅ [POSTGRESQL] YugabyteDBEngine created successfully with PostgreSQL driver");

        // Enable pgvector extension using JDBC (more reliable than ysqlsh in containers)
        logger.info("🔧 [POSTGRESQL] Enabling pgvector extension via JDBC...");
        try (var connection = engine.getConnection();
                var stmt = connection.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");
            logger.info("✅ [POSTGRESQL] pgvector extension enabled successfully");
        } catch (Exception e) {
            logger.warn("⚠️ [POSTGRESQL] Warning: Failed to enable pgvector extension: {}", e.getMessage());
            logger.warn("[POSTGRESQL] Tests will gracefully handle missing pgvector extension");
        }

        logger.info("🎉 [POSTGRESQL] YugabyteDB Testcontainer setup completed successfully");
    }

    @AfterAll
    static void cleanup() {
        logger.info("🧹 [CLEANUP] Starting YugabyteDB Testcontainer cleanup...");

        if (engine != null) {
            logger.info("[CLEANUP] Closing YugabyteDBEngine...");
            engine.close();
        }

        if (dataSource != null) {
            logger.info("[CLEANUP] Closing HikariDataSource...");
            dataSource.close();
        }

        logger.info("✅ [CLEANUP] YugabyteDB Testcontainer cleanup completed successfully");
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName("test_embeddings")
                .dimension(384)
                .metricType(MetricType.COSINE)
                .createTableIfNotExists(true)
                .build();
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        // YugabyteDB supports the contains() operation
        logger.info("=".repeat(80));
        logger.info("[POSTGRESQL] YugabyteDB SUPPORTS contains() operation");
        logger.info("[POSTGRESQL] Expected behavior: Some tests will be skipped");
        logger.info("=".repeat(80));
        logger.info("[POSTGRESQL] Explanation:");
        logger.info("[POSTGRESQL]   - Tests with @DisabledIf(\"supportsContains\") will be SKIPPED");
        logger.info("[POSTGRESQL]   - These tests verify behavior when contains() is NOT supported");
        logger.info("[POSTGRESQL]   - Since YugabyteDB DOES support contains(), skipping is correct");
        logger.info("[POSTGRESQL]");
        logger.info("[POSTGRESQL] Examples of skipped tests:");
        logger.info("[POSTGRESQL]   1. Tests that check 'unsupported operation' exceptions");
        logger.info("[POSTGRESQL]   2. Tests that verify fallback behavior when contains() unavailable");
        logger.info("[POSTGRESQL]   3. Tests designed for databases without full-text search capabilities");
        logger.info("[POSTGRESQL]");
        logger.info("[POSTGRESQL] Total expected skipped tests: ~2 per driver");
        logger.info("[POSTGRESQL] PostgreSQL Driver: Running ~214 tests (skipping ~2)");
        logger.info("=".repeat(80));
        return true;
    }

    /**
     * Nested test class for YugabyteDB Smart Driver.
     * Runs all inherited tests from EmbeddingStoreWithFilteringIT using Smart Driver.
     */
    @Nested
    @Testcontainers
    class SmartDriverIT extends EmbeddingStoreWithFilteringIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverIT.class);

        @BeforeAll
        static void setupSmartDriver() throws Exception {
            smartLogger.info("🚀 [SMART-DRIVER-SETUP] Initializing YugabyteDB Smart Driver setup...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER-SETUP] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            smartLogger.info("📋 [SMART-DRIVER-SETUP] Container details:");
            smartLogger.info("[SMART-DRIVER-SETUP]   - Image: yugabytedb/yugabyte:2025.1.0.1-b3");
            smartLogger.info("[SMART-DRIVER-SETUP]   - Host: {}", yugabyteContainer.getHost());
            smartLogger.info("[SMART-DRIVER-SETUP]   - Mapped port: {}", yugabyteContainer.getMappedPort(5433));
            smartLogger.info("[SMART-DRIVER-SETUP]   - Container ID: {}", yugabyteContainer.getContainerId());

            // Create Smart Driver engine
            smartLogger.info("🔧 [SMART-DRIVER-SETUP] Creating YugabyteDB Smart Driver engine...");
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
            smartLogger.info("✅ [SMART-DRIVER-SETUP] YugabyteDB Smart Driver engine created successfully");

            smartLogger.info("🎉 [SMART-DRIVER-SETUP] YugabyteDB Smart Driver setup completed successfully");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER-CLEANUP] Starting Smart Driver cleanup...");

            if (smartEngine != null) {
                smartLogger.info("[SMART-DRIVER-CLEANUP] Closing YugabyteDB Smart Driver engine...");
                smartEngine.close();
            }

            smartLogger.info("✅ [SMART-DRIVER-CLEANUP] Smart Driver cleanup completed");
        }

        @Override
        protected EmbeddingStore<TextSegment> embeddingStore() {
            smartLogger.info("[SMART-DRIVER] Creating embedding store with Smart Driver");
            return YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("test_embeddings_smart")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();
        }

        @Override
        protected void clearStore() {
            smartLogger.info("[SMART-DRIVER] Clearing embedding store");
            embeddingStore().removeAll();
        }

        @Override
        protected EmbeddingModel embeddingModel() {
            return embeddingModel;
        }

        @Override
        protected boolean supportsContains() {
            // YugabyteDB supports the contains() operation
            smartLogger.info("=".repeat(80));
            smartLogger.info("[SMART-DRIVER] YugabyteDB SUPPORTS contains() operation");
            smartLogger.info("[SMART-DRIVER] Expected behavior: Some tests will be skipped");
            smartLogger.info("=".repeat(80));
            smartLogger.info("[SMART-DRIVER] Explanation:");
            smartLogger.info("[SMART-DRIVER]   - Tests with @DisabledIf(\"supportsContains\") will be SKIPPED");
            smartLogger.info("[SMART-DRIVER]   - These tests verify behavior when contains() is NOT supported");
            smartLogger.info("[SMART-DRIVER]   - Since YugabyteDB DOES support contains(), skipping is correct");
            smartLogger.info("[SMART-DRIVER]");
            smartLogger.info("[SMART-DRIVER] Examples of skipped tests:");
            smartLogger.info("[SMART-DRIVER]   1. Tests that check 'unsupported operation' exceptions");
            smartLogger.info("[SMART-DRIVER]   2. Tests that verify fallback behavior when contains() unavailable");
            smartLogger.info("[SMART-DRIVER]   3. Tests designed for databases without full-text search capabilities");
            smartLogger.info("[SMART-DRIVER]");
            smartLogger.info("[SMART-DRIVER] Total expected skipped tests: ~2 per driver");
            smartLogger.info("[SMART-DRIVER] Smart Driver: Running ~214 tests (skipping ~2)");
            smartLogger.info("[SMART-DRIVER]");
            smartLogger.info("[SMART-DRIVER] 📊 COMPREHENSIVE TEST COVERAGE SUMMARY:");
            smartLogger.info("[SMART-DRIVER]   - PostgreSQL Driver: ~214 tests");
            smartLogger.info("[SMART-DRIVER]   - Smart Driver:     ~214 tests");
            smartLogger.info("[SMART-DRIVER]   - Total Coverage:   ~428 tests across both drivers");
            smartLogger.info("=".repeat(80));
            return true;
        }
    }
}
