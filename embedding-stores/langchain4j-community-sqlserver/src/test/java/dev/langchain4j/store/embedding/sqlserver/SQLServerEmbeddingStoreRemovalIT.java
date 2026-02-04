package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil.DEFAULT_CONTAINER;
import static dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil.getSqlServerDataSource;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SQLServerEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static String tableName = "test_remove_" + nextInt(1000, 2000);
    static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    static EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
            .dataSource(getSqlServerDataSource())
            .embeddingTable(EmbeddingTable.builder()
                    .name(tableName)
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .dimension(embeddingModel.dimension())
                    .build())
            .build();

    @Test
    void config() {
        assertThat(embeddingStore).isNotNull();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @AfterEach
    void after() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @BeforeEach
    void before() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @AfterAll
    static void afterClass() {
        DEFAULT_CONTAINER.stop();
    }
}
