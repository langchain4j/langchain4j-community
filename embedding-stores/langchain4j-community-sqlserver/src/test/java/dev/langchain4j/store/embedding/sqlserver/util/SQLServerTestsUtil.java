package dev.langchain4j.store.embedding.sqlserver.util;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;

public class SQLServerTestsUtil {

    public static final MSSQLServerContainer<?> DEFAULT_CONTAINER = new MSSQLServerContainer<>(
                    "mcr.microsoft.com/mssql/server:2025-latest")
            .withEnv("MSSQL_COLLATION", "SQL_Latin1_General_CP1_CS_AS");

    public static @NonNull SQLServerDataSource getSqlServerDataSource() {
        return getSqlServerDataSource(DEFAULT_CONTAINER);
    }

    public static @NonNull SQLServerDataSource getSqlServerDataSource(JdbcDatabaseContainer<?> sqlServerContainer) {
        sqlServerContainer.start();

        String host = normalizeSqlServerHost(sqlServerContainer.getHost());
        Integer port = sqlServerContainer.getMappedPort(1433);
        String username = sqlServerContainer.getUsername();
        String password = sqlServerContainer.getPassword();

        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(host);
        dataSource.setPortNumber(port);
        dataSource.setUser(username);
        dataSource.setPassword(password);
        dataSource.setEncrypt("false");
        dataSource.setTrustServerCertificate(true);

        ensureAcceptsConnections(dataSource);
        return dataSource;
    }

    static String normalizeSqlServerHost(String host) {
        Objects.requireNonNull(host, "host");
        if ("localhost".equalsIgnoreCase(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) {
            return "127.0.0.1";
        }
        return host;
    }

    private static void ensureAcceptsConnections(SQLServerDataSource dataSource) {
        SQLException last = null;

        for (int attempt = 1; attempt <= 20; attempt++) {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                last = e;
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for SQL Server to accept connections", e);
            }
        }

        throw new IllegalStateException("Timed out waiting for SQL Server to accept connections", last);
    }

    /**
     * Provides a set of predefined text segments representing descriptions of popular Japanese dishes.
     * Each text segment includes the description and associated metadata, such as the name
     * of the dish in both Japanese and English.
     *
     * @return an array of {@code TextSegment} objects, each representing a Japanese dish with
     * associated text and metadata.
     */
    public static TextSegment[] japaneseSampling() {
        return new TextSegment[] {
            TextSegment.from(
                    "寿司は、酢飯と魚介類を組み合わせた日本の代表的な料理です。海外では**「SUSHI」として人気**です。",
                    Metadata.from(Map.of("name", "Sushi", "jap_name", "寿司 (すし)"))),
            TextSegment.from(
                    "味噌汁は、出汁に味噌を溶かした汁物で、豆腐やわかめなどの具を入れます。「ミソ」は日本の伝統的な調味料です。",
                    Metadata.from(Map.of("name", "Miso Soup", "jap_name", "味噌汁 (みそしる)"))),
            TextSegment.from(
                    "天ぷらは、魚介類や野菜を衣で包んで揚げた料理です。サクサクとした食感が特長で、世界中で楽しまれています。",
                    Metadata.from(Map.of("name", "Tempura", "jap_name", "天ぷら (てんぷら)"))),
            TextSegment.from(
                    "ラーメンは、中華麺を出汁の効いたスープに入れた料理で、トッピングにはチャーシューや卵が使われます。「ラ-メン」は若者に大人気です。",
                    Metadata.from(Map.of("name", "Ramen", "jap_name", "ラーメン"))),
        };
    }
}
