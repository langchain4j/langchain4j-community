package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

class ArcadeDBEmbeddingStoreEmbeddedIT extends EmbeddingStoreWithFilteringIT {

    @TempDir
    Path tempDir;

    private ArcadeDBEmbeddingStore embeddingStore;

    private static final int DIMENSION = 384;
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
                    .databasePath(
                            tempDir.resolve("test-db-" + COUNTER.incrementAndGet()).toString())
                    .dimension(DIMENSION)
                    .maxConnections(16)
                    .beamWidth(200)
                    .build();
        }
        return embeddingStore;
    }

    @AfterEach
    void teardown() {
        if (embeddingStore != null) {
            embeddingStore.close();
            embeddingStore = null;
        }
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }
}
