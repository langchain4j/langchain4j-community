package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil.getSqlServerDataSource;
import static org.junit.jupiter.api.Assertions.*;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

class SQLServerEmbeddingStoreConfigIT {

    @Test
    void basicConfigTest() {
        SQLServerDataSource dataSource = getSqlServerDataSource();
        SQLServerEmbeddingStore embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE_OR_REPLACE)
                        .name("my_embedding_table")
                        .idColumn("id_column_name")
                        .embeddingColumn("embedding_column_name")
                        .textColumn("text_column_name")
                        .metadataColumn("metadata_column_name")
                        .dimension(4)
                        .build())
                .build();
        assertNotNull(embeddingStore);
    }

    @Test
    void shouldCreateTableWithJsonIndexes() {
        SQLServerDataSource dataSource = getSqlServerDataSource();

        // Create embedding table with JSON index
        EmbeddingTable embeddingTable = EmbeddingTable.builder()
                .name("test_table")
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .dimension(4)
                .build();

        JSONIndexBuilder jsonIndex = new JSONIndexBuilder()
                .key("author", String.class, JSONIndexBuilder.Order.ASC)
                .key("year", Integer.class, JSONIndexBuilder.Order.DESC);
        jsonIndex.createOption(CreateOption.CREATE_OR_REPLACE);

        EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(embeddingTable)
                .addIndex(jsonIndex.build())
                .build();

        // Test that store was created successfully
        assertNotNull(embeddingStore);
    }

    @Test
    void shouldCreateTableWithOrderedJsonIndex() {
        SQLServerDataSource dataSource = getSqlServerDataSource();

        EmbeddingTable embeddingTable = EmbeddingTable.builder()
                .name("test_vector_index")
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .dimension(4)
                .build();

        SQLServerEmbeddingStore embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(embeddingTable)
                .addIndex(Index.jsonIndexBuilder()
                        .createOption(CreateOption.CREATE_OR_REPLACE)
                        .key("author", String.class, JSONIndexBuilder.Order.ASC)
                        .key("year", Integer.class, JSONIndexBuilder.Order.DESC)
                        .build())
                .build();

        // Test that store was created successfully
        assertNotNull(embeddingStore);

        // Test basic functionality
        Embedding embedding = new Embedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f});
        TextSegment textSegment = TextSegment.from("test text");

        String id = embeddingStore.add(embedding, textSegment);
        assertNotNull(id);
    }
}
