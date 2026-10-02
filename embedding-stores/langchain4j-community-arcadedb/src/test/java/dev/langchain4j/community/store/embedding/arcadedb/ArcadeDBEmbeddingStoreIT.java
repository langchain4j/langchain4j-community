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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

class ArcadeDBEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static GenericContainer<?> arcadedb;
    private static ArcadeDBEmbeddingStore embeddingStore;
    private static String host;
    private static int port;

    @BeforeAll
    static void startServer() {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            arcadedb = ArcadeDBTestServer.newContainer();
            arcadedb.start();
            host = arcadedb.getHost();
            port = arcadedb.getMappedPort(ArcadeDBTestServer.HTTP_PORT);
        } else {
            host = "localhost";
            port = ArcadeDBTestServer.HTTP_PORT;
        }

        embeddingStore = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .maxConnections(128)
                .beamWidth(500)
                .createDatabase(true)
                .build();
    }

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

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
        embeddingStore().removeAll();
    }

    @Override
    protected boolean testFloatExactly() {
        return false;
    }

    @Override
    protected boolean testDoubleExactly() {
        // ArcadeDB cannot store Double.MIN_VALUE (4.9E-324) precisely — it underflows to 0.0.
        return false;
    }

    @Override
    protected double doublePercentage() {
        // Double.MIN_VALUE underflows to 0.0, which is 100% off. Use 200 to give clear margin
        // above the boundary where subnormal floating-point arithmetic can cause a false failure.
        return 200;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @org.junit.jupiter.api.Test
    void should_perform_hybrid_search() {
        assertHybridSearch(embeddingStore(), embeddingModel);
    }

    @org.junit.jupiter.api.Test
    void should_reopen_existing_store_with_different_index_settings() {
        // ArcadeDB 26.8.1+ rejects CREATE INDEX IF NOT EXISTS when the existing index has another configuration.
        // Like embedded mode, the store keeps the existing index.
        ArcadeDBEmbeddingStore reopened = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .maxConnections(16)
                .beamWidth(100)
                .build();

        Embedding embedding = embeddingModel.embed("reopened store").content();
        String id = reopened.add(embedding);

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build())
                .matches();
        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }
}
