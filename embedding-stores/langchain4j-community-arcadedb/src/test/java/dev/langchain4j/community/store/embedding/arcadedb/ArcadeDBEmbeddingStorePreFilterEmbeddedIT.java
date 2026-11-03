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

/** The LangChain4j filter suite with every filter applied in SQL ({@link ArcadeDBFilterStrategy#PRE_FILTER}), embedded. */
class ArcadeDBEmbeddingStorePreFilterEmbeddedIT extends EmbeddingStoreWithFilteringIT {

    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @TempDir
    Path tempDir;

    private ArcadeDBEmbeddingStore embeddingStore;

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
                    .databasePath(tempDir.resolve("pre-filter-db-" + COUNTER.incrementAndGet())
                            .toString())
                    .dimension(384)
                    .filterStrategy(ArcadeDBFilterStrategy.PRE_FILTER)
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
