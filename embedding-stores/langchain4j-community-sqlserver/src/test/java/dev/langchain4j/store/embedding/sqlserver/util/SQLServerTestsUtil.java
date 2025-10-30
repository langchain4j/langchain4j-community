package dev.langchain4j.store.embedding.sqlserver.util;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.sqlserver.SQLServerEmbeddingStore;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;

import java.util.Map;
import java.util.Properties;

public class SQLServerTestsUtil {

    public static final MSSQLServerContainer DEFAULT_CONTAINER =
            (MSSQLServerContainer) new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2025-latest")
                    .withEnv("MSSQL_COLLATION", "SQL_Latin1_General_CP1_CS_AS");

    public static @NotNull SQLServerDataSource getSqlServerDataSource() {
        return getSqlServerDataSource(DEFAULT_CONTAINER);
    }

    public static @NotNull SQLServerDataSource getSqlServerDataSource(JdbcDatabaseContainer sqlServerContainer) {
        sqlServerContainer.start();

        String host = sqlServerContainer.getHost();
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
        return dataSource;
    }

    public static @NotNull SQLServerEmbeddingStore.SQLServerEmbeddingStoreConnectionBuilder getConnectionBuilder() {
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("encrypt", "false");
        connectionProperties.setProperty("trustServerCertificate", "true");
        connectionProperties.setProperty("loginTimeout", "30");
        connectionProperties.setProperty("connectTimeout", "5000");
        connectionProperties.setProperty("applicationName", "LangChain4j SQLServer EmbeddingStore");

        String host = DEFAULT_CONTAINER.getHost();
        Integer port = DEFAULT_CONTAINER.getMappedPort(1433);
        String username = DEFAULT_CONTAINER.getUsername();
        String password = DEFAULT_CONTAINER.getPassword();
        String database = DEFAULT_CONTAINER.getDatabaseName();

        SQLServerEmbeddingStore.SQLServerEmbeddingStoreConnectionBuilder builder = SQLServerEmbeddingStore.connectionBuilder();
        builder.host(host);
        builder.port(port);
        builder.userName(username);
        builder.password(password);
        builder.database(database);
        builder.connectionProperties(connectionProperties);
        return builder;
    }

    /**
     * Provides a set of predefined text segments representing descriptions of popular Japanese dishes.
     * Each text segment includes the description and associated metadata, such as the name
     * of the dish in both Japanese and English.
     *
     * @return an array of {@code TextSegment} objects, each representing a Japanese dish with
     *         associated text and metadata.
     */
    public static TextSegment[] japaneseSampling() {
        return new TextSegment[]{
                TextSegment.from("寿司は、酢飯と魚介類を組み合わせた日本の代表的な料理です。海外では**「SUSHI」として人気**です。", Metadata.from(Map.of("name", "Sushi", "jap_name", "寿司 (すし)"))),
                TextSegment.from("味噌汁は、出汁に味噌を溶かした汁物で、豆腐やわかめなどの具を入れます。「ミソ」は日本の伝統的な調味料です。", Metadata.from(Map.of("name", "Miso Soup", "jap_name", "味噌汁 (みそしる)"))),
                TextSegment.from("天ぷらは、魚介類や野菜を衣で包んで揚げた料理です。サクサクとした食感が特長で、世界中で楽しまれています。", Metadata.from(Map.of("name", "Tempura", "jap_name", "天ぷら (てんぷら)"))),
                TextSegment.from("ラーメンは、中華麺を出汁の効いたスープに入れた料理で、トッピングにはチャーシューや卵が使われます。「ラ-メン」は若者に大人気です。", Metadata.from(Map.of("name", "Ramen", "jap_name", "ラーメン"))),
        };
    }
}


