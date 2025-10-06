package dev.langchain4j.community.store.embedding.yugabytedb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.community.store.memory.chat.yugabytedb.YugabyteDBChatMemoryStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for YugabyteDB integration tests using Testcontainers.
 * Provides common container setup, driver configuration, and cleanup functionality.
 */
@Testcontainers
public abstract class YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBTestBase.class);

    protected static final String DB_NAME = "yugabyte";
    protected static final String DB_USER = "yugabyte";
    protected static final String DB_PASSWORD = "yugabyte";

    @Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> yugabyteContainer = new GenericContainer<>(
                    DockerImageName.parse("yugabytedb/yugabyte:2025.1.0.1-b3"))
            .withExposedPorts(5433, 7000, 9000, 15433, 9042)
            .withCommand("bin/yugabyted", "start", "--background=false")
            .waitingFor(Wait.forListeningPorts(5433).withStartupTimeout(Duration.ofMinutes(5)));

    protected static EmbeddingModel embeddingModel;
    protected static YugabyteDBEngine engine;
    protected static HikariDataSource dataSource;

    @BeforeAll
    static void setupBase() throws Exception {
        logger.info("🚀 [SETUP] Initializing YugabyteDB Testcontainer setup...");

        logger.info("🧠 [SETUP] Creating embedding model (AllMiniLmL6V2QuantizedEmbeddingModel)...");
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        logger.info("✅ [SETUP] Embedding model created successfully");

        logger.info("📋 [SETUP] Container details:");
        logger.info("[SETUP]   - Image: yugabytedb/yugabyte:2025.1.0.1-b3");
        logger.info("[SETUP]   - Host: {}", yugabyteContainer.getHost());
        logger.info("[SETUP]   - Mapped port: {}", yugabyteContainer.getMappedPort(5433));
        logger.info("[SETUP]   - Container ID: {}", yugabyteContainer.getContainerId());

        // Wait for YugabyteDB to be ready
        logger.info("⏳ [SETUP] Waiting 5 seconds for YugabyteDB to fully initialize...");
        Thread.sleep(5000);

        // Create PostgreSQL driver engine (recommended)
        logger.info("🔧 [SETUP] Creating YugabyteDBEngine with PostgreSQL driver...");
        logger.info("🔧 [SETUP] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");
        logger.info(
                "🔧 [SETUP] Connection URL: jdbc:postgresql://{}:{}/{}",
                yugabyteContainer.getHost(),
                yugabyteContainer.getMappedPort(5433),
                DB_NAME);

        engine = createPostgreSQLEngine();
        logger.info("✅ [SETUP] YugabyteDBEngine created successfully with PostgreSQL driver");

        // Enable pgvector extension using JDBC (more reliable than ysqlsh in containers)
        logger.info("🔧 [SETUP] Enabling pgvector extension via JDBC...");
        try (var connection = engine.getConnection();
                var stmt = connection.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");
            logger.info("✅ [SETUP] pgvector extension enabled successfully");
        } catch (Exception e) {
            logger.warn("⚠️ [SETUP] Warning: Failed to enable pgvector extension: {}", e.getMessage());
            logger.warn("[SETUP] Tests will gracefully handle missing pgvector extension");
        }

        logger.info("🎉 [SETUP] YugabyteDB Testcontainer setup completed successfully");
        logger.info(
                "🔗 [SETUP] Connection endpoint: {}:{}",
                yugabyteContainer.getHost(),
                yugabyteContainer.getMappedPort(5433));
    }

    @AfterAll
    static void cleanupBase() {
        logger.info("🧹 [CLEANUP] Starting YugabyteDB Testcontainer cleanup...");

        if (engine != null) {
            logger.info("[CLEANUP] Closing YugabyteDBEngine...");
            engine.close();
        }

        if (dataSource != null) {
            logger.info("[CLEANUP] Closing HikariDataSource...");
            dataSource.close();
        }

        logger.info("[CLEANUP] Stopping container: {}", yugabyteContainer.getContainerId());
        yugabyteContainer.stop();

        logger.info("✅ [CLEANUP] YugabyteDB Testcontainer cleanup completed successfully");
    }

    /**
     * Creates a YugabyteDBEngine using PostgreSQL JDBC driver (recommended approach)
     */
    protected static YugabyteDBEngine createPostgreSQLEngine() {
        return YugabyteDBEngine.builder()
                .host(yugabyteContainer.getHost())
                .port(yugabyteContainer.getMappedPort(5433))
                .database(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .usePostgreSQLDriver(true)
                .maxPoolSize(20) // Higher pool size for performance tests
                .build();
    }

    /**
     * Creates a YugabyteDBEngine using YugabyteDB Smart Driver with custom pool configuration
     */
    protected static YugabyteDBEngine createSmartDriverEngine(int maxPoolSize) throws Exception {
        return createSmartDriverEngine(maxPoolSize, "YugabyteSmartDriverPool");
    }

    /**
     * Creates a YugabyteDBEngine using YugabyteDB Smart Driver with custom pool configuration and pool name
     */
    protected static YugabyteDBEngine createSmartDriverEngine(int maxPoolSize, String poolName) throws Exception {
        logger.info("🔧 [SETUP] Configuring YugabyteDB Smart Driver with pool: {}", poolName);
        logger.info("🔧 [SETUP] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");
        logger.info(
                "🔧 [SETUP] Connection URL: jdbc:yugabytedb://{}:{}/{}",
                yugabyteContainer.getHost(),
                yugabyteContainer.getMappedPort(5433),
                DB_NAME);

        Properties poolProperties = createSmartDriverProperties(maxPoolSize, poolName);

        HikariConfig config = new HikariConfig(poolProperties);
        config.validate();
        HikariDataSource smartDataSource = new HikariDataSource(config);

        logger.info("✅ [SETUP] Smart Driver engine created successfully");
        return YugabyteDBEngine.from(smartDataSource);
    }

    /**
     * Creates standardized Smart Driver properties for YugabyteDB connection
     */
    protected static Properties createSmartDriverProperties(int maxPoolSize, String poolName) {
        Properties poolProperties = new Properties();

        // DataSource configuration
        poolProperties.setProperty("dataSourceClassName", "com.yugabyte.ysql.YBClusterAwareDataSource");
        poolProperties.setProperty("maximumPoolSize", String.valueOf(maxPoolSize));
        poolProperties.setProperty("minimumIdle", Math.max(1, maxPoolSize / 4) + ""); // Dynamic minimum idle
        poolProperties.setProperty("connectionTimeout", "10000");
        poolProperties.setProperty("poolName", poolName);

        // YugabyteDB connection properties using TestContainer
        poolProperties.setProperty("dataSource.serverName", yugabyteContainer.getHost());
        poolProperties.setProperty("dataSource.portNumber", String.valueOf(yugabyteContainer.getMappedPort(5433)));
        poolProperties.setProperty("dataSource.databaseName", DB_NAME);
        poolProperties.setProperty("dataSource.user", DB_USER);
        poolProperties.setProperty("dataSource.password", DB_PASSWORD);

        // Disable load balancing for single node container
        poolProperties.setProperty("dataSource.loadBalance", "false");

        // Performance optimizations
        poolProperties.setProperty("dataSource.prepareThreshold", "1");
        poolProperties.setProperty("dataSource.reWriteBatchedInserts", "true");
        poolProperties.setProperty("dataSource.tcpKeepAlive", "true");
        poolProperties.setProperty("dataSource.socketTimeout", "0");
        poolProperties.setProperty("dataSource.loginTimeout", "10");

        return poolProperties;
    }

    /**
     * Creates a YugabyteDBEngine using PostgreSQL driver with custom pool size
     */
    protected static YugabyteDBEngine createPostgreSQLEngine(int maxPoolSize) {
        return YugabyteDBEngine.builder()
                .host(yugabyteContainer.getHost())
                .port(yugabyteContainer.getMappedPort(5433))
                .database(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .usePostgreSQLDriver(true)
                .maxPoolSize(maxPoolSize)
                .build();
    }

    /**
     * Creates a YugabyteDBEmbeddingStore with standard configuration
     */
    protected YugabyteDBEmbeddingStore createStore(String tableName) {
        return YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName(tableName)
                .dimension(384)
                .metricType(MetricType.COSINE)
                .createTableIfNotExists(true)
                .build();
    }

    /**
     * Creates a YugabyteDBEmbeddingStore with custom configuration
     */
    protected YugabyteDBEmbeddingStore createStore(String tableName, int dimension, MetricType metricType) {
        return YugabyteDBEmbeddingStore.builder()
                .engine(engine)
                .tableName(tableName)
                .dimension(dimension)
                .metricType(metricType)
                .createTableIfNotExists(true)
                .build();
    }

    /**
     * Creates a YugabyteDBChatMemoryStore with PostgreSQL driver
     */
    protected ChatMemoryStore createChatMemoryStore(String tableName) {
        return YugabyteDBChatMemoryStore.builder()
                .engine(engine)
                .tableName(tableName)
                .createTableIfNotExists(true)
                .build();
    }

    /**
     * Creates a YugabyteDBChatMemoryStore with custom engine (for Smart Driver tests)
     */
    protected ChatMemoryStore createChatMemoryStore(YugabyteDBEngine customEngine, String tableName) {
        return YugabyteDBChatMemoryStore.builder()
                .engine(customEngine)
                .tableName(tableName)
                .createTableIfNotExists(true)
                .build();
    }

    /**
     * Creates a YugabyteDBChatMemoryStore with TTL support
     */
    protected ChatMemoryStore createChatMemoryStoreWithTTL(String tableName, java.time.Duration ttl) {
        return YugabyteDBChatMemoryStore.builder()
                .engine(engine)
                .tableName(tableName)
                .ttl(ttl)
                .createTableIfNotExists(true)
                .build();
    }

    /**
     * Utility method to drop test tables for cleanup
     */
    protected void dropTestTables(String... tableNames) {
        logger.info("🧹 [CLEANUP] Dropping test tables...");

        try (var connection = engine.getConnection();
                var statement = connection.createStatement()) {

            for (String tableName : tableNames) {
                try {
                    statement.execute("DROP TABLE IF EXISTS " + tableName + " CASCADE");
                    logger.info("[CLEANUP] Dropped table: {}", tableName);
                } catch (Exception e) {
                    logger.warn("⚠️ [CLEANUP] Warning dropping table {}: {}", tableName, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.warn("⚠️ [CLEANUP] Error during table cleanup: {}", e.getMessage());
        }
    }
}
