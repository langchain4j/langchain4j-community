package dev.langchain4j.community.store.embedding.oceanbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;

/**
 * Integration tests for {@link OceanBaseEmbeddingStore}.
 */
public class OceanBaseEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private OceanBaseEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStore();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @AfterAll
    static void cleanUp() throws SQLException {
        try {
            CommonTestOperations.dropTable();
        } finally {
            CommonTestOperations.stopContainer();
        }
    }
    
    @AfterEach
    void tearDown() {
        clearStore();
    }
    
    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
    
    @Override
    protected boolean supportsContains() {
        return true;
    }
}
