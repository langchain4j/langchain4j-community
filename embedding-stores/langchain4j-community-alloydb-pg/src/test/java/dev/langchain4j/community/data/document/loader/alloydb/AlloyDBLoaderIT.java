package dev.langchain4j.community.data.document.loader.alloydb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.alloydb.AlloyDBEngine;
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
class AlloyDBLoaderIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private static AlloyDBEngine engine;
    private static Connection connection;

    @BeforeAll
    static void beforeAll() throws SQLException {
        engine = new AlloyDBEngine.Builder()
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
    static void afterAll() {
        engine.close();
    }

    @Test
    void should_load_documents_from_database() throws Exception {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertThat(documents).hasSize(2);

        assertThat(documents.get(0).text()).isEqualTo("test content 1");
        assertThat(documents.get(0).metadata().toMap()).containsEntry("key", "value1");
        assertThat(documents.get(0).metadata().toMap()).containsEntry("metadata", "test metadata 1");

        assertThat(documents.get(1).text()).isEqualTo("test content 2");
        assertThat(documents.get(1).metadata().toMap()).containsEntry("key", "value2");
        assertThat(documents.get(1).metadata().toMap()).containsEntry("metadata", "test metadata 2");
    }

    @Test
    void should_load_documents_with_custom_query() throws Exception {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .query("SELECT content, metadata, langchain_metadata FROM test_table WHERE id = 1")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertThat(documents).hasSize(1);

        assertThat(documents.get(0).text()).isEqualTo("test content 1");
        assertThat(documents.get(0).metadata().toMap()).containsEntry("key", "value1");
        assertThat(documents.get(0).metadata().toMap()).containsEntry("metadata", "test metadata 1");
    }

    @Test
    void should_load_documents_with_text_formatter() throws Exception {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("text")
                .build();

        List<Document> documents = loader.load();

        assertThat(documents.get(0).text()).isEqualTo("test content 1");
        assertThat(documents.get(1).text()).isEqualTo("test content 2");
    }

    @Test
    void should_load_documents_with_csv_formatter() throws Exception {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("csv")
                .build();

        List<Document> documents = loader.load();

        assertThat(documents.get(0).text()).isEqualTo("test content 1,");
        assertThat(documents.get(1).text()).isEqualTo("test content 2,");
    }

    @Test
    void should_load_documents_with_yaml_formatter() throws Exception {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("YAML")
                .build();

        List<Document> documents = loader.load();

        assertThat(documents.get(0).text()).isEqualTo("content: test content 1");
        assertThat(documents.get(1).text()).isEqualTo("content: test content 2");
    }

    @Test
    void should_load_documents_With_json_formatter() throws Exception {
        AlloyDBLoader loader = new AlloyDBLoader.Builder(engine)
                .tableName("test_table")
                .contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .format("JSON")
                .build();

        List<Document> documents = loader.load();

        assertThat(documents.get(0).text()).isEqualTo("{\"content\":\"test content 1\"}");
        assertThat(documents.get(1).text()).isEqualTo("{\"content\":\"test content 2\"}");
    }
}
