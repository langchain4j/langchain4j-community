package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

public class ArcadeDBEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static GenericContainer<?> arcadedb;
    private static EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void startServer() {
        String host;
        int port;

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
                .databaseName("test_removal")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .createDatabase(true)
                .build();
    }

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void clearStore() {
        embeddingStore().removeAll();
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
