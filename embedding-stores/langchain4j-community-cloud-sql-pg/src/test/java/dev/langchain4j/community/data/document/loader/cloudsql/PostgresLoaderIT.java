package dev.langchain4j.community.data.document.loader.cloudsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.community.store.embedding.cloudsql.PostgresEngine;
import dev.langchain4j.data.document.Document;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresLoaderIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private static PostgresEngine engine;
    private static Connection connection;

    @BeforeAll
    static void beforeAll() throws SQLException {
        engine = new PostgresEngine.Builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .build();
        connection = engine.getConnection();
    }

    @BeforeEach
    void setUp() throws SQLException {
        createTableAndInsertData();
    }

    private void createTableAndInsertData() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE test_table (id SERIAL PRIMARY KEY, content TEXT, metadata TEXT, langchain_metadata JSONB)");
            statement.execute(
                    "INSERT INTO test_table (content, metadata, langchain_metadata) VALUES ('test content 1', 'test metadata 1', '{\"key\": \"value1\"}')");
            statement.execute(
                    "INSERT INTO test_table (content, metadata, langchain_metadata) VALUES ('test content 2', 'test metadata 2', '{\"key\": \"value2\"}')");
        }
    }

    @AfterEach
    void afterEach() throws SQLException {
        connection.createStatement().executeUpdate("DROP TABLE IF EXISTS test_table");
    }

    @AfterAll
    static void afterAll() throws SQLException {
        engine.close();
    }

    @Test
    void testLoadDocumentsFromDatabase() throws SQLException {
        PostgresLoader loader = new PostgresLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertNotNull(documents);
        assertEquals(2, documents.size());

        assertEquals("test content 1", documents.get(0).text());
        assertEquals("value1", documents.get(0).metadata().toMap().get("key"));
        assertEquals("test metadata 1", documents.get(0).metadata().toMap().get("metadata"));

        assertEquals("test content 2", documents.get(1).text());
        assertEquals("value2", documents.get(1).metadata().toMap().get("key"));
        assertEquals("test metadata 2", documents.get(1).metadata().toMap().get("metadata"));
    }

    @Test
    void testLoadDocumentsWithCustomQuery() throws SQLException {
        PostgresLoader loader = new PostgresLoader.Builder(engine)
                .query("SELECT content, metadata, langchain_metadata FROM test_table WHERE id = 1")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertNotNull(documents);
        assertEquals(1, documents.size());

        assertEquals("test content 1", documents.get(0).text());
        assertEquals("value1", documents.get(0).metadata().toMap().get("key"));
        assertEquals("test metadata 1", documents.get(0).metadata().toMap().get("metadata"));
    }

    @Test
    void testLoadDocumentsWithTextFormatter() throws SQLException {
        PostgresLoader loader = new PostgresLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("text")
                .build();

        List<Document> documents = loader.load();

        assertEquals("test content 1", documents.get(0).text());
        assertEquals("test content 2", documents.get(1).text());
    }

    @Test
    void testLoadDocumentsWithCsvFormatter() throws SQLException {
        PostgresLoader loader = new PostgresLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("csv")
                .build();

        List<Document> documents = loader.load();

        assertEquals("test content 1,", documents.get(0).text());
        assertEquals("test content 2,", documents.get(1).text());
    }

    @Test
    void testLoadDocumentsWithYamlFormatter() throws SQLException {
        PostgresLoader loader = new PostgresLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("YAML")
                .build();

        List<Document> documents = loader.load();

        assertEquals("content: test content 1", documents.get(0).text());
        assertEquals("content: test content 2", documents.get(1).text());
    }

    @Test
    void testLoadDocumentsWithJsonFormatter() throws SQLException {
        PostgresLoader loader = new PostgresLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("JSON")
                .build();

        List<Document> documents = loader.load();

        assertEquals("{\"content\":\"test content 1\"}", documents.get(0).text());
        assertEquals("{\"content\":\"test content 2\"}", documents.get(1).text());
    }
}
