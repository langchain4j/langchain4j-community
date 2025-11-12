package dev.langchain4j.community.store.embedding.jvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterEach;

class JVectorEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    EmbeddingStore<TextSegment> embeddingStore = JVectorEmbeddingStore.builder()
            .dimension(384)
            .build();

    @AfterEach
    void afterEach() {
        embeddingStore.removeAll();
    }

    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false;
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
