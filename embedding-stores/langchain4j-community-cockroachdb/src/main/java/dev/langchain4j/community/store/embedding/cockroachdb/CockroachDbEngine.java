package dev.langchain4j.community.store.embedding.cockroachdb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CockroachDbEngine is a thin wrapper around a {@link DataSource} that provides
 * pooled connections for the CockroachDB embedding store and chat memory store.
 *
 * <p>The {@link Builder} accepts the canonical Postgres-wire host/port/db/user/password
 * tuple. If you already have a {@link DataSource}, use {@link #from(DataSource)}.
 *
 * <p>If you only have a connection string, use {@link #fromConnectionString(String)}.
 * The {@code cockroachdb://} scheme used by the Python library is rewritten to
 * {@code jdbc:postgresql://}. {@code postgresql://} and {@code postgres://} are
 * also accepted and rewritten. JDBC-prefixed URLs are passed through unchanged.
 */
public class CockroachDbEngine {

    private static final Logger logger = LoggerFactory.getLogger(CockroachDbEngine.class);

    private final DataSource dataSource;

    public CockroachDbEngine(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CockroachDbEngine from(DataSource dataSource) {
        return new CockroachDbEngine(dataSource);
    }

    public static CockroachDbEngine fromConnectionString(String connectionString) {
        return new Builder().connectionString(connectionString).build();
    }

    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new CockroachDbRequestFailedException("Failed to get CockroachDB connection", e);
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    /**
     * Convert a Python-style {@code cockroachdb://} or libpq-style {@code postgresql://}
     * URL into the {@code jdbc:postgresql://} form expected by the PgJDBC driver.
     */
    static String toJdbcUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("connection string must not be empty");
        }
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("cockroachdb+psycopg://")) {
            return "jdbc:postgresql://" + url.substring("cockroachdb+psycopg://".length());
        }
        if (url.startsWith("cockroachdb://")) {
            return "jdbc:postgresql://" + url.substring("cockroachdb://".length());
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.substring("postgresql://".length());
        }
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        throw new IllegalArgumentException("Unsupported connection-string scheme: " + url);
    }

    public static class Builder {
        private String connectionString;
        private String host = "localhost";
        private Integer port = 26257;
        private String database = "defaultdb";
        private String username = "root";
        private String password = "";
        private String schema = "public";
        private String sslMode = "disable";
        private String applicationName = "langchain4j-cockroachdb";
        private Integer maxPoolSize = 10;
        private Integer minPoolSize = 5;
        private long connectionTimeoutMs = 10_000L;
        private long idleTimeoutMs = 300_000L;
        private long maxLifetimeMs = 3_600_000L;

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

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

        public Builder sslMode(String sslMode) {
            this.sslMode = sslMode;
            return this;
        }

        public Builder applicationName(String applicationName) {
            this.applicationName = applicationName;
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

        public Builder connectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        public Builder idleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
            return this;
        }

        public Builder maxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
            return this;
        }

        public CockroachDbEngine build() {
            HikariConfig config = new HikariConfig();

            String jdbcUrl;
            if (connectionString != null && !connectionString.isEmpty()) {
                jdbcUrl = toJdbcUrl(connectionString);
            } else {
                jdbcUrl = String.format(
                        "jdbc:postgresql://%s:%d/%s?currentSchema=%s&sslmode=%s&ApplicationName=%s",
                        host, port, database, schema, sslMode, applicationName);
            }
            config.setJdbcUrl(jdbcUrl);
            // Apply credentials regardless of which URL path was used so that callers can
            // pass a connection string plus username/password (the common Testcontainers shape).
            if (username != null) {
                config.setUsername(username);
            }
            if (password != null) {
                config.setPassword(password);
            }
            config.setDriverClassName("org.postgresql.Driver");

            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minPoolSize);
            config.setConnectionTimeout(connectionTimeoutMs);
            config.setIdleTimeout(idleTimeoutMs);
            config.setMaxLifetime(maxLifetimeMs);

            // PgJDBC defaults to prepareThreshold=5 already; left as default.
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.addDataSourceProperty("reWriteBatchedInserts", "true");

            logger.info("Initializing CockroachDB connection pool: {}", jdbcUrl);

            try {
                return new CockroachDbEngine(new HikariDataSource(config));
            } catch (Exception e) {
                throw new CockroachDbRequestFailedException("Failed to create CockroachDB connection pool", e);
            }
        }
    }
}
