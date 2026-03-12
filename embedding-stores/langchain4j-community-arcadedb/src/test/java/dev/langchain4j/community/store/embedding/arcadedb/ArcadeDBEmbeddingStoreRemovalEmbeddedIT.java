package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class ArcadeDBEmbeddingStoreRemovalEmbeddedIT extends EmbeddingStoreWithRemovalIT {

    @TempDir
    Path tempDir;

    private ArcadeDBEmbeddingStore embeddingStore;

    private static final int DIMENSION = 384;
    private static final EmbeddingModel EMBEDDING_MODEL = new TestEmbeddingModel(DIMENSION);
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @BeforeEach
    void setup() {
        embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("test-removal-db-" + COUNTER.incrementAndGet()).toString())
                .dimension(DIMENSION)
                .build();
    }

    @AfterEach
    void teardown() {
        if (embeddingStore != null) {
            embeddingStore.close();
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }
}
