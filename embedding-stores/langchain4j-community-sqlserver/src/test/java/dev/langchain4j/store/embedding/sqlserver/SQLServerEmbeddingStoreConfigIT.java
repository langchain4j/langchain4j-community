package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil.getSqlServerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.sqlserver.exception.SQLServerLangChain4jException;
import org.junit.jupiter.api.Test;

class SQLServerEmbeddingStoreConfigIT {

    @Test
    void basic_config_test() {
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
        assertThat(embeddingStore).isNotNull();
    }

    @Test
    void create_if_not_exists_config_test() {
        SQLServerDataSource dataSource = getSqlServerDataSource();
        SQLServerEmbeddingStore embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE_OR_REPLACE)
                        .name("my_embedding_table")
                        .dimension(4)
                        .build())
                .build();
        assertThat(embeddingStore).isNotNull();

        SQLServerEmbeddingStore.Builder embeddingStoreBuilder = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE)
                        .name("my_embedding_table")
                        .dimension(4)
                        .build());
        assertThatExceptionOfType(SQLServerLangChain4jException.class).isThrownBy(embeddingStoreBuilder::build);

        SQLServerEmbeddingStore embeddingStore2 = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                        .name("my_embedding_table")
                        .dimension(4)
                        .build())
                .build();

        assertThat(embeddingStore2).isNotNull();
    }

    @Test
    void create_if_not_exists_custom_catalog_config_test() {
        SQLServerDataSource dataSource = getSqlServerDataSource();
        SQLServerEmbeddingStore embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE_OR_REPLACE)
                        .catalogName("master")
                        .schemaName("dbo")
                        .name("my_embedding_table")
                        .dimension(4)
                        .build())
                .build();
        assertThat(embeddingStore).isNotNull();

        SQLServerEmbeddingStore.Builder embeddingStoreBuilder = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE)
                        .catalogName("master")
                        .schemaName("dbo")
                        .name("my_embedding_table")
                        .dimension(4)
                        .build());
        assertThatExceptionOfType(SQLServerLangChain4jException.class).isThrownBy(embeddingStoreBuilder::build);

        SQLServerEmbeddingStore embeddingStore2 = SQLServerEmbeddingStore.dataSourceBuilder()
                .dataSource(dataSource)
                .embeddingTable(EmbeddingTable.builder()
                        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                        .catalogName("master")
                        .schemaName("dbo")
                        .name("my_embedding_table")
                        .dimension(4)
                        .build())
                .build();

        assertThat(embeddingStore2).isNotNull();
    }

    @Test
    void should_create_table_with_json_indexes() {
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
        assertThat(embeddingStore).isNotNull();
    }

    @Test
    void index_with_create_if_not_exists_may_fail() {

        JSONIndexBuilder jsonIndex = new JSONIndexBuilder()
                .key("author", String.class, JSONIndexBuilder.Order.ASC)
                .key("year", Integer.class, JSONIndexBuilder.Order.DESC);
        jsonIndex.createOption(CreateOption.CREATE_IF_NOT_EXISTS);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(jsonIndex::build);
    }

    @Test
    void should_create_table_with_ordered_json_index() {
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
        assertThat(embeddingStore).isNotNull();

        // Test basic functionality
        Embedding embedding = new Embedding(new float[] {0.1f, 0.2f, 0.3f, 0.4f});
        TextSegment textSegment = TextSegment.from("test text");

        String id = embeddingStore.add(embedding, textSegment);
        assertThat(id).isNotNull();
    }

    @Test
    void should_detect_sql_injection() {
        EmbeddingTable.Builder embeddingTable = EmbeddingTable.builder()
                .name("test_vector_index as select 1;drop database master;create table test")
                .dimension(4);

        // Test that store was created successfully
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(embeddingTable::build);
    }
}
