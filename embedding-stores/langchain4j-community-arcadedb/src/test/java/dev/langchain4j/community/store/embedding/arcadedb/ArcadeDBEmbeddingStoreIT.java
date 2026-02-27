package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

class ArcadeDBEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @TempDir
    Path tempDir;

    private ArcadeDBEmbeddingStore embeddingStore;
    private static final int DIMENSION = 384;
    private static final EmbeddingModel embeddingModel = new TestEmbeddingModel(DIMENSION);
    private static final AtomicInteger counter = new AtomicInteger();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            embeddingStore = ArcadeDBEmbeddingStore.builder()
                    .databasePath(tempDir.resolve("test-db-" + counter.incrementAndGet()).toString())
                    .dimension(DIMENSION)
                    .ef(200)
                    .efConstruction(400)
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
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }
}
