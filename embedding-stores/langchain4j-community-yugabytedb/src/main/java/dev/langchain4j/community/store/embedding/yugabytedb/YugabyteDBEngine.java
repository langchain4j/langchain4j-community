package dev.langchain4j.community.store.embedding.yugabytedb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YugabyteDBEngine is a wrapper around a {@link DataSource} that provides
 * basic connectivity and vector type registration for YugabyteDB.
 */
public class YugabyteDBEngine {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBEngine.class);

    private final DataSource dataSource;

    /**
     * Constructor for YugabyteDBEngine with custom DataSource
     *
     * @param dataSource the DataSource to use for connections
     */
    public YugabyteDBEngine(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Get a connection from the pool
     *
     * @return SQL Connection
     * @throws YugabyteDBRequestFailedException if connection fails
     */
    public Connection getConnection() {
        try {
            Connection connection = dataSource.getConnection();
            // Note: Vector type registration is now handled in the store methods
            // where prepared statements are used, following the pattern from other implementations
            return connection;
        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to get database connection", e);
        }
    }

    /**
     * Close the engine and cleanup resources
     */
    public void close() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    /**
     * Builder for YugabyteDBEngine
     */
    public static class Builder {
        private String host = "localhost";
        private Integer port = 5433; // Default YugabyteDB port
        private String database = "yugabyte";
        private String username = "yugabyte";
        private String password = "";
        private String schema = "public";
        private boolean useSsl = false;
        private String sslMode = "disable";
        private Integer maxPoolSize = 10;
        private Integer minPoolSize = 5;
        private String connectionTimeout = "10000";
        private String idleTimeout = "300000";
        private String maxLifetime = "900000";
        private String applicationName = "langchain4j-yugabytedb";
        private boolean usePostgreSQLDriver = false; // Use PostgreSQL driver instead of YugabyteDB driver

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder useSsl(boolean useSsl) {
            this.useSsl = useSsl;
            return this;
        }

        public Builder sslMode(String sslMode) {
            this.sslMode = sslMode;
            return this;
        }

        public Builder maxPoolSize(Integer maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder minPoolSize(Integer minPoolSize) {
            this.minPoolSize = minPoolSize;
            return this;
        }

        public Builder connectionTimeout(String connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder idleTimeout(String idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder maxLifetime(String maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        /**
         * Use PostgreSQL JDBC driver instead of YugabyteDB driver (recommended by YugabyteDB docs)
         * Reference: https://docs.yugabyte.com/preview/drivers-orms/java/postgres-jdbc-reference/
         */
        public Builder usePostgreSQLDriver(boolean usePostgreSQLDriver) {
            this.usePostgreSQLDriver = usePostgreSQLDriver;
            return this;
        }

        public YugabyteDBEngine build() {
            HikariConfig config = new HikariConfig();

            // Choose driver and URL format based on configuration
            String jdbcUrl;
            String driverClassName;

            if (usePostgreSQLDriver) {
                // PostgreSQL JDBC driver (recommended approach per YugabyteDB docs)
                jdbcUrl = String.format(
                        "jdbc:postgresql://%s:%d/%s?currentSchema=%s&ssl=%s&sslmode=%s&ApplicationName=%s",
                        host, port, database, schema, useSsl, sslMode, applicationName);
                driverClassName = "org.postgresql.Driver";
                logger.info("Using PostgreSQL JDBC driver (YugabyteDB recommended approach)");
            } else {
                // YugabyteDB specific driver
                jdbcUrl = String.format(
                        "jdbc:yugabytedb://%s:%d/%s?currentSchema=%s&ssl=%s&sslmode=%s&ApplicationName=%s",
                        host, port, database, schema, useSsl, sslMode, applicationName);
                driverClassName = "com.yugabyte.Driver";
                logger.info("Using YugabyteDB specific JDBC driver");
            }

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName(driverClassName);

            // Connection pool settings
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minPoolSize);
            config.setConnectionTimeout(Long.parseLong(connectionTimeout));
            config.setIdleTimeout(Long.parseLong(idleTimeout));
            config.setMaxLifetime(Long.parseLong(maxLifetime));

            // YugabyteDB specific optimizations
            config.addDataSourceProperty("prepareThreshold", "1");
            config.addDataSourceProperty("reWriteBatchedInserts", "true");
            config.addDataSourceProperty("defaultRowFetchSize", "1000");

            // Performance tuning
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.addDataSourceProperty("socketTimeout", "0");
            config.addDataSourceProperty("loginTimeout", "10");

            logger.info("Connecting to YugabyteDB at {}:{}/{}", host, port, database);

            try {
                HikariDataSource dataSource = new HikariDataSource(config);
                return new YugabyteDBEngine(dataSource);
            } catch (Exception e) {
                throw new YugabyteDBRequestFailedException("Failed to create YugabyteDB connection pool", e);
            }
        }
    }

    /**
     * Create a builder for YugabyteDBEngine
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create YugabyteDBEngine from existing DataSource
     *
     * @param dataSource the DataSource to use
     * @return YugabyteDBEngine instance
     */
    public static YugabyteDBEngine from(DataSource dataSource) {
        return new YugabyteDBEngine(dataSource);
    }
}
