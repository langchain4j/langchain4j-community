package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingStoreHybridSearchTestHelper.assertHybridSearch;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.nio.file.Path;
import java.util.List;
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
                    .databasePath(tempDir.resolve("test-db-" + COUNTER.incrementAndGet())
                            .toString())
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

    @org.junit.jupiter.api.Test
    void should_perform_hybrid_search() {
        assertHybridSearch(embeddingStore(), embeddingModel());
    }

    @org.junit.jupiter.api.Test
    void should_reopen_existing_store_with_different_index_settings() {
        String path = tempDir.resolve("reopened-db").toString();
        Embedding embedding = EMBEDDING_MODEL.embed("reopened store").content();
        String id;
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(path)
                .dimension(DIMENSION)
                .maxConnections(16)
                .beamWidth(200)
                .build()) {
            id = store.add(embedding);
        }

        // The existing index is kept; the different settings do not fail the builder.
        try (ArcadeDBEmbeddingStore reopened = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(path)
                .dimension(DIMENSION)
                .maxConnections(32)
                .beamWidth(100)
                .build()) {
            List<EmbeddingMatch<TextSegment>> matches = reopened.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding)
                            .maxResults(1)
                            .build())
                    .matches();
            assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
        }
    }
}
