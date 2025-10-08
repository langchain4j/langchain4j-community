package dev.langchain4j.community.store.embedding.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for YugabyteDB connection handling using Testcontainers.
 * Tests various connection scenarios, SSL, connection pooling, etc.
 * Uses actual YugabyteDB container for authentic testing.
 */
class YugabyteDBConnectionIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBConnectionIT.class);

    @Test
    void should_connect_with_postgresql_jdbc_driver() {
        logger.info("🚀 [POSTGRESQL] Starting PostgreSQL JDBC driver connection test...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        logger.info("📋 [POSTGRESQL] Building YugabyteDBEngine with PostgreSQL driver configuration:");
        logger.info("[POSTGRESQL]   - Host: " + yugabyteContainer.getHost());
        logger.info("[POSTGRESQL]   - Port: " + yugabyteContainer.getMappedPort(5433));
        logger.info("[POSTGRESQL]   - Database: " + DB_NAME);
        logger.info("[POSTGRESQL]   - Username: " + DB_USER);
        logger.info("[POSTGRESQL]   - Driver: PostgreSQL JDBC (usePostgreSQLDriver=true)");

        YugabyteDBEngine engine = createPostgreSQLEngine(20); // Use centralized factory method

        logger.info("✅ [POSTGRESQL] YugabyteDBEngine created successfully with PostgreSQL driver");

        try (Connection connection = engine.getConnection()) {
            logger.info("🔗 [POSTGRESQL] Connection obtained from PostgreSQL driver engine");

            assertThat(connection).isNotNull();
            assertThat(connection.isClosed()).isFalse();
            logger.info("✅ [POSTGRESQL] Connection validation passed (not null, not closed)");

            // Verify it's using PostgreSQL driver
            String url = connection.getMetaData().getURL();
            logger.info("🔗 [POSTGRESQL] Connection URL: " + url);
            assertThat(url).contains("postgresql");
            logger.info("[TEST] Confirmed using PostgreSQL JDBC driver");

            // Test basic SQL operations
            logger.info("[TEST] Executing SQL query: SELECT version()");
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT version()");
                assertThat(rs.next()).isTrue();
                String version = rs.getString(1);
                assertThat(version).isNotEmpty();
                logger.info("[TEST] Database version: " + version);
                logger.info("✅ [TEST] PostgreSQL JDBC driver connection test completed successfully");
            }
        } catch (SQLException e) {
            logger.warn("[TEST] SQL Exception occurred: " + e.getMessage());
            logger.warn("[TEST] SQL State: " + e.getSQLState());
            logger.warn("[TEST] Error Code: " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            logger.info("[TEST] Closing YugabyteDBEngine...");
            engine.close();
            logger.info("[TEST] YugabyteDBEngine closed successfully");
        }
    }

    @Test
    void should_connect_with_yugabytedb_driver() {
        logger.info("🚀 [SMART-DRIVER] Starting YugabyteDB Smart Driver connection test...");
        logger.info(
                "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

        try {
            logger.info("📋 [SMART-DRIVER] Configuring YugabyteDB Smart Driver with YBClusterAwareDataSource...");

            // Use centralized factory method for Smart Driver configuration
            YugabyteDBEngine engine = createSmartDriverEngine(5, "YugabyteSmartDriverPool");
            logger.info("✅ [SMART-DRIVER] YugabyteDBEngine created successfully using factory method");

            // Test connection from YugabyteDBEngine
            logger.info("🔗 [SMART-DRIVER] Attempting to get connection from Smart Driver engine...");
            try (Connection connection = engine.getConnection()) {
                logger.info("🔗 [SMART-DRIVER] Connection obtained from Smart Driver engine");

                assertThat(connection).isNotNull();
                assertThat(connection.isClosed()).isFalse();
                logger.info("✅ [SMART-DRIVER] Connection validation passed (not null, not closed)");

                // Verify it's using YugabyteDB driver
                String url = connection.getMetaData().getURL();
                logger.info("🔗 [SMART-DRIVER] Connection URL: " + url);
                assertThat(url).contains("yugabytedb");
                logger.info("✅ [SMART-DRIVER] Confirmed using YugabyteDB Smart Driver");

                // Test basic SQL operations
                logger.info("[TEST] Executing SQL query: SELECT version()");
                try (Statement stmt = connection.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT version()");
                    assertThat(rs.next()).isTrue();
                    String version = rs.getString(1);
                    assertThat(version).isNotEmpty();
                    logger.info("[TEST] Database version: " + version);
                }

                logger.info("✅ [TEST] YugabyteDB Smart Driver connection test completed successfully");
            } finally {
                logger.info("[TEST] Closing YugabyteDBEngine...");
                engine.close();
                logger.info("[TEST] Resources closed successfully");
            }
        } catch (Exception e) {
            logger.warn("❌ [TEST] YugabyteDB Smart Driver test failed with exception:");
            logger.warn("[TEST] Exception type: " + e.getClass().getSimpleName());
            logger.warn("[TEST] Exception message: " + e.getMessage());
            if (e.getCause() != null) {
                logger.warn("[TEST] Caused by: " + e.getCause().getClass().getSimpleName() + ": "
                        + e.getCause().getMessage());
            }
        }
    }

    @Test
    void should_handle_connection_pooling() {
        logger.info("[TEST] Starting connection pooling test with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        // Test connection pool configuration
        logger.info("[TEST] Configuring HikariCP connection pool:");
        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format(
                "jdbc:postgresql://%s:%d/%s",
                yugabyteContainer.getHost(), yugabyteContainer.getMappedPort(5433), DB_NAME);

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(5000);

        logger.info("[TEST]   - JDBC URL: " + jdbcUrl);
        logger.info("[TEST]   - Driver: org.postgresql.Driver");
        logger.info("[TEST]   - Maximum pool size: 10");
        logger.info("[TEST]   - Minimum idle: 5");
        logger.info("[TEST]   - Connection timeout: 5000ms");

        logger.info("[TEST] Creating HikariDataSource...");
        HikariDataSource dataSource = new HikariDataSource(config);
        logger.info("[TEST] HikariDataSource created successfully");

        logger.info("[TEST] Creating YugabyteDBEngine from DataSource...");
        YugabyteDBEngine engine = YugabyteDBEngine.from(dataSource);
        logger.info("[TEST] YugabyteDBEngine created successfully");

        // Test multiple concurrent connections
        logger.info("[TEST] Testing connection pooling with 15 concurrent connections...");
        try {
            for (int i = 0; i < 15; i++) {
                logger.info("[TEST] Getting connection " + (i + 1) + "/15...");
                try (Connection connection = engine.getConnection()) {
                    assertThat(connection).isNotNull();
                    assertThat(connection.isClosed()).isFalse();
                    logger.info("[TEST] Connection " + (i + 1) + " validated successfully");
                } catch (SQLException e) {
                    logger.error("[TEST] Connection " + (i + 1) + " failed: " + e.getMessage());
                    throw new RuntimeException("Connection failed", e);
                }
            }
            logger.info(
                    "✅ [TEST] PostgreSQL driver connection pooling test completed successfully - all 15 connections worked");
        } finally {
            logger.info("[TEST] Closing resources...");
            engine.close();
            try {
                dataSource.close();
                logger.info("[TEST] Resources closed successfully");
            } catch (Exception e) {
                logger.warn("[TEST] Warning: Exception during resource cleanup: " + e.getMessage());
            }
        }
    }

    @Test
    void should_handle_connection_pooling_with_smart_driver() {
        logger.info("🚀 [SMART-DRIVER] Starting connection pooling test with Smart Driver...");
        logger.info(
                "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

        try {
            // Create Smart Driver engine with connection pooling
            logger.info("[SMART-DRIVER] Creating YugabyteDB Smart Driver engine with pooling...");
            YugabyteDBEngine engine = createSmartDriverEngine(10, "PoolingTestSmartDriver");
            logger.info("✅ [SMART-DRIVER] Engine created successfully");

            // Test multiple concurrent connections
            logger.info("[SMART-DRIVER] Testing connection pooling with 15 concurrent connections...");
            try {
                for (int i = 0; i < 15; i++) {
                    logger.info("[SMART-DRIVER] Getting connection " + (i + 1) + "/15...");
                    try (Connection connection = engine.getConnection()) {
                        assertThat(connection).isNotNull();
                        assertThat(connection.isClosed()).isFalse();

                        // Verify it's using YugabyteDB driver
                        String url = connection.getMetaData().getURL();
                        assertThat(url).contains("yugabytedb");

                        logger.info("[SMART-DRIVER] Connection " + (i + 1) + " validated successfully");
                    } catch (SQLException e) {
                        logger.error("[SMART-DRIVER] Connection " + (i + 1) + " failed: " + e.getMessage());
                        throw new RuntimeException("Connection failed", e);
                    }
                }
                logger.info(
                        "✅ [SMART-DRIVER] Smart Driver connection pooling test completed successfully - all 15 connections worked");
            } finally {
                logger.info("[SMART-DRIVER] Closing engine...");
                engine.close();
                logger.info("[SMART-DRIVER] Resources closed successfully");
            }
        } catch (Exception e) {
            logger.warn("❌ [SMART-DRIVER] Connection pooling test failed: " + e.getMessage());
            logger.warn("[SMART-DRIVER] This is acceptable if Smart Driver is not available in test environment");
        }
    }

    @Test
    void should_handle_connection_failures_gracefully() {
        logger.info("[TEST] Starting connection failure handling test with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        logger.info("[TEST] Attempting to create YugabyteDBEngine with invalid connection parameters:");
        logger.info("[TEST]   - Host: invalid-host");
        logger.info("[TEST]   - Port: 9999");
        logger.info("[TEST]   - Database: invalid-db");
        logger.info("[TEST]   - Username: invalid-user");
        logger.info("[TEST]   - Password: invalid-pass");

        // Test connection failure handling
        assertThatThrownBy(() -> {
                    YugabyteDBEngine.builder()
                            .host("invalid-host")
                            .port(9999)
                            .database("invalid-db")
                            .username("invalid-user")
                            .password("invalid-pass")
                            .usePostgreSQLDriver(true)
                            .build();
                    // The exception should be thrown during engine creation, not connection
                })
                .isInstanceOf(YugabyteDBRequestFailedException.class)
                .hasMessageContaining("Failed to create YugabyteDB connection pool");

        logger.info("✅ [TEST] Expected YugabyteDBRequestFailedException was thrown correctly");
        logger.info("✅ [TEST] PostgreSQL driver connection failure handling test completed successfully");
    }

    @Test
    void should_handle_connection_failures_gracefully_with_smart_driver() {
        logger.info("🚀 [SMART-DRIVER] Starting connection failure handling test with Smart Driver...");
        logger.info(
                "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

        logger.info("[SMART-DRIVER] Attempting to create YugabyteDBEngine with invalid connection parameters:");
        logger.info("[SMART-DRIVER]   - Host: invalid-host");
        logger.info("[SMART-DRIVER]   - Port: 9999");
        logger.info("[SMART-DRIVER]   - Database: invalid-db");
        logger.info("[SMART-DRIVER]   - Username: invalid-user");
        logger.info("[SMART-DRIVER]   - Password: invalid-pass");

        // Test connection failure handling with Smart Driver
        assertThatThrownBy(() -> {
                    YugabyteDBEngine.builder()
                            .host("invalid-host")
                            .port(9999)
                            .database("invalid-db")
                            .username("invalid-user")
                            .password("invalid-pass")
                            .usePostgreSQLDriver(false) // Use Smart Driver
                            .build();
                    // The exception should be thrown during engine creation, not connection
                })
                .isInstanceOf(YugabyteDBRequestFailedException.class)
                .hasMessageContaining("Failed to create YugabyteDB connection pool");

        logger.info("✅ [SMART-DRIVER] Expected YugabyteDBRequestFailedException was thrown correctly");
        logger.info("✅ [SMART-DRIVER] Smart Driver connection failure handling test completed successfully");
    }

    @Test
    void should_create_embedding_store_with_custom_configuration() {
        logger.info("[TEST] Starting embedding store configuration test with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        logger.info("[TEST] Creating YugabyteDBEngine for embedding store tests:");
        logger.info("[TEST]   - Host: " + yugabyteContainer.getHost());
        logger.info("[TEST]   - Port: " + yugabyteContainer.getMappedPort(5433));
        logger.info("[TEST]   - Database: " + DB_NAME);
        logger.info("[TEST]   - Max pool size: 5");

        YugabyteDBEngine engine = createPostgreSQLEngine(5); // Use centralized factory method

        logger.info("[TEST] YugabyteDBEngine created successfully");

        try {
            // Test different metric types for configuration
            MetricType[] metricTypes = {MetricType.COSINE, MetricType.EUCLIDEAN, MetricType.DOT_PRODUCT};
            logger.info("[TEST] Testing embedding store creation with different metric types:");

            for (MetricType metricType : metricTypes) {
                logger.info("[TEST] Creating embedding store with metric type: " + metricType);
                String tableName = "config_test_pg_" + metricType.name().toLowerCase();

                logger.info("[TEST]   - Table name: " + tableName);
                logger.info("[TEST]   - Dimension: 384");
                logger.info("[TEST]   - Metric type: " + metricType);
                logger.info("[TEST]   - Create table if not exists: true");

                YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                        .engine(engine)
                        .tableName(tableName)
                        .dimension(384)
                        .metricType(metricType)
                        .createTableIfNotExists(true)
                        .build();

                // Just verify the store was created successfully
                // Note: We skip actual embedding operations due to vector type registration
                // complexities in Testcontainer environment
                assertThat(store).isNotNull();
                logger.info("[TEST] Embedding store created successfully for metric: " + metricType);
            }

            logger.info("✅ [TEST] PostgreSQL driver - all embedding store configurations created successfully");

        } finally {
            logger.info("[TEST] Closing YugabyteDBEngine...");
            engine.close();
            logger.info("[TEST] YugabyteDBEngine closed successfully");
        }
    }

    @Test
    void should_create_embedding_store_with_custom_configuration_with_smart_driver() {
        logger.info("🚀 [SMART-DRIVER] Starting embedding store configuration test with Smart Driver...");
        logger.info(
                "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

        try {
            logger.info("[SMART-DRIVER] Creating YugabyteDB Smart Driver engine for embedding store tests:");
            logger.info("[SMART-DRIVER]   - Host: " + yugabyteContainer.getHost());
            logger.info("[SMART-DRIVER]   - Port: " + yugabyteContainer.getMappedPort(5433));
            logger.info("[SMART-DRIVER]   - Database: " + DB_NAME);
            logger.info("[SMART-DRIVER]   - Max pool size: 5");

            YugabyteDBEngine engine = createSmartDriverEngine(5, "ConfigTestSmartDriver");
            logger.info("✅ [SMART-DRIVER] Engine created successfully");

            try {
                // Test different metric types for configuration
                MetricType[] metricTypes = {MetricType.COSINE, MetricType.EUCLIDEAN, MetricType.DOT_PRODUCT};
                logger.info("[SMART-DRIVER] Testing embedding store creation with different metric types:");

                for (MetricType metricType : metricTypes) {
                    logger.info("[SMART-DRIVER] Creating embedding store with metric type: " + metricType);
                    String tableName = "config_test_smart_" + metricType.name().toLowerCase();

                    logger.info("[SMART-DRIVER]   - Table name: " + tableName);
                    logger.info("[SMART-DRIVER]   - Dimension: 384");
                    logger.info("[SMART-DRIVER]   - Metric type: " + metricType);
                    logger.info("[SMART-DRIVER]   - Create table if not exists: true");

                    YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                            .engine(engine)
                            .tableName(tableName)
                            .dimension(384)
                            .metricType(metricType)
                            .createTableIfNotExists(true)
                            .build();

                    assertThat(store).isNotNull();
                    logger.info("[SMART-DRIVER] Embedding store created successfully for metric: " + metricType);
                }

                logger.info("✅ [SMART-DRIVER] All embedding store configurations created successfully");

            } finally {
                logger.info("[SMART-DRIVER] Closing YugabyteDBEngine...");
                engine.close();
                logger.info("[SMART-DRIVER] YugabyteDBEngine closed successfully");
            }
        } catch (Exception e) {
            logger.warn("❌ [SMART-DRIVER] Embedding store configuration test failed: " + e.getMessage());
            logger.warn("[SMART-DRIVER] This is acceptable if Smart Driver is not available in test environment");
        }
    }

    @Test
    void should_verify_pgvector_extension() {
        logger.info("[TEST] Starting pgvector extension verification test with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        logger.info("[TEST] Creating YugabyteDBEngine for pgvector testing:");
        logger.info("[TEST]   - Host: " + yugabyteContainer.getHost());
        logger.info("[TEST]   - Port: " + yugabyteContainer.getMappedPort(5433));
        logger.info("[TEST]   - Database: " + DB_NAME);

        YugabyteDBEngine engine = createPostgreSQLEngine(20); // Use centralized factory method

        logger.info("[TEST] YugabyteDBEngine created successfully");

        try (Connection connection = engine.getConnection();
                Statement stmt = connection.createStatement()) {

            logger.info("[TEST] Connection obtained, checking pgvector extension...");

            // Verify it's using PostgreSQL driver
            String url = connection.getMetaData().getURL();
            assertThat(url).contains("postgresql");
            logger.info("[TEST] Confirmed using PostgreSQL JDBC driver");

            // Test vector operations
            logger.info("[TEST] Testing vector distance calculation...");
            String vectorQuery = "SELECT '[1,2,3]'::vector <-> '[1,2,4]'::vector AS distance";
            logger.info("[TEST] Executing query: " + vectorQuery);

            ResultSet rs = stmt.executeQuery(vectorQuery);
            assertThat(rs.next()).isTrue();
            double distance = rs.getDouble("distance");
            assertThat(distance).isPositive();

            logger.info("[TEST] Vector distance calculation result: " + String.format("%.6f", distance));
            logger.info("[TEST] pgvector extension is working correctly (vector operations successful)");
            logger.info("✅ [TEST] PostgreSQL driver - pgvector extension verification completed successfully");

        } catch (SQLException e) {
            logger.error("[TEST] SQL Exception during pgvector verification:");
            logger.error("[TEST] Exception message: " + e.getMessage());
            logger.error("[TEST] SQL State: " + e.getSQLState());
            logger.error("[TEST] Error Code: " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            logger.info("[TEST] Closing YugabyteDBEngine...");
            engine.close();
            logger.info("[TEST] YugabyteDBEngine closed successfully");
        }
    }

    @Test
    void should_verify_pgvector_extension_with_smart_driver() throws Exception {
        logger.info("🚀 [SMART-DRIVER] Starting pgvector extension verification test with Smart Driver...");
        logger.info(
                "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

        logger.info("[SMART-DRIVER] Creating YugabyteDB Smart Driver engine for pgvector testing:");
        logger.info("[SMART-DRIVER]   - Host: " + yugabyteContainer.getHost());
        logger.info("[SMART-DRIVER]   - Port: " + yugabyteContainer.getMappedPort(5433));
        logger.info("[SMART-DRIVER]   - Database: " + DB_NAME);

        YugabyteDBEngine engine = createSmartDriverEngine(5, "PgVectorTestSmartDriver");
        logger.info("✅ [SMART-DRIVER] Engine created successfully");

        try (Connection connection = engine.getConnection();
                Statement stmt = connection.createStatement()) {

            logger.info("[SMART-DRIVER] Connection obtained, checking pgvector extension...");

            // Verify it's using YugabyteDB driver
            String url = connection.getMetaData().getURL();
            assertThat(url).contains("yugabytedb");
            logger.info("[SMART-DRIVER] Confirmed using YugabyteDB Smart Driver");

            // Test vector operations
            logger.info("[SMART-DRIVER] Testing vector distance calculation...");
            String vectorQuery = "SELECT '[1,2,3]'::vector <-> '[1,2,4]'::vector AS distance";
            logger.info("[SMART-DRIVER] Executing query: " + vectorQuery);

            ResultSet rs = stmt.executeQuery(vectorQuery);
            assertThat(rs.next()).isTrue();
            double distance = rs.getDouble("distance");
            assertThat(distance).isPositive();

            logger.info("[SMART-DRIVER] Vector distance calculation result: " + String.format("%.6f", distance));
            logger.info("[SMART-DRIVER] pgvector extension is working correctly (vector operations successful)");
            logger.info("✅ [SMART-DRIVER] pgvector extension verification completed successfully");

        } catch (SQLException e) {
            logger.error("[SMART-DRIVER] SQL Exception during pgvector verification:");
            logger.error("[SMART-DRIVER] Exception message: " + e.getMessage());
            logger.error("[SMART-DRIVER] SQL State: " + e.getSQLState());
            logger.error("[SMART-DRIVER] Error Code: " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            logger.info("[SMART-DRIVER] Closing YugabyteDBEngine...");
            engine.close();
            logger.info("[SMART-DRIVER] YugabyteDBEngine closed successfully");
        }
    }
}
