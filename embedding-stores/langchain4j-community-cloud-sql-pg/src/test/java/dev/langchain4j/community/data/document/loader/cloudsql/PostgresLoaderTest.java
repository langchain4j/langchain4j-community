package dev.langchain4j.community.data.document.loader.cloudsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.store.embedding.cloudsql.PostgresEngine;
import dev.langchain4j.data.document.Document;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * This class represents unit tests for {@link PostgresLoader}.
 */
class PostgresLoaderTest {

    @Mock
    private PostgresEngine mockPostgresEngine;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockStatement;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private ResultSetMetaData mockResultSetMetaData;

    private PostgresLoader.Builder builder;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(mockPostgresEngine.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockResultSetMetaData);
        builder = new PostgresLoader.Builder(mockPostgresEngine);
        builder.tableName("testTable");
    }

    @Test
    void testBuildWithQuery() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(2);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");
        when(mockResultSetMetaData.getColumnName(2)).thenReturn("col2");

        PostgresLoader loader = builder.query("SELECT * FROM test_table").build();

        assertNotNull(loader);
    }

    @Test
    void testBuildWithTableName() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(2);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");
        when(mockResultSetMetaData.getColumnName(2)).thenReturn("col2");

        PostgresLoader loader = builder.tableName("test_table").build();

        assertNotNull(loader);
    }

    @Test
    void testBuildWithInvalidColumns() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(1);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");

        builder.contentColumns(List.of("invalid_col"));

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    void testBuildWithInvalidMetadataJsonColumn() throws SQLException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(1);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("col1");

        builder.metadataJsonColumn("invalid_json_col");

        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    void testLoadDocuments() throws SQLException, ExecutionException, InterruptedException {
        when(mockResultSetMetaData.getColumnCount()).thenReturn(3);
        when(mockResultSetMetaData.getColumnName(1)).thenReturn("content");
        when(mockResultSetMetaData.getColumnName(2)).thenReturn("metadata");
        when(mockResultSetMetaData.getColumnName(3)).thenReturn("langchain_metadata");

        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("content")).thenReturn("test content");
        when(mockResultSet.getObject("metadata")).thenReturn("test metadata");
        when(mockResultSet.getObject("langchain_metadata")).thenReturn("{\"key\":\"value\"}");

        PostgresLoader loader = builder.contentColumns(List.of("content"))
                .metadataColumns(List.of("metadata"))
                .metadataJsonColumn("langchain_metadata")
                .build();

        List<Document> documents = loader.load();

        assertEquals(1, documents.size());
        assertEquals("test content", documents.get(0).text());
        assertEquals("value", documents.get(0).metadata().toMap().get("key"));
        assertEquals("test metadata", documents.get(0).metadata().toMap().get("metadata"));
    }
}
