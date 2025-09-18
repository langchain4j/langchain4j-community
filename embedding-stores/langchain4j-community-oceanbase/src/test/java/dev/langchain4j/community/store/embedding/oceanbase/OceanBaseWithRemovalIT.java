package dev.langchain4j.community.store.embedding.oceanbase;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.SQLException;

/**
 * Tests for {@link OceanBaseEmbeddingStore} with removal.
 */
public class OceanBaseWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    private OceanBaseEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void setUp() {
        embeddingStore = CommonTestOperations.newEmbeddingStore();
    }

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
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
