package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class ArcadeDBEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final int ARCADE_HTTP_PORT = 2480;

    private static GenericContainer<?> arcadedb;
    private static EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void startServer() {
        String host;
        int port;

        if (DockerClientFactory.instance().isDockerAvailable()) {
            arcadedb = new GenericContainer<>("arcadedata/arcadedb:latest")
                    .withExposedPorts(ARCADE_HTTP_PORT)
                    .withEnv("JAVA_OPTS", "-Darcadedb.server.rootPassword=playwithdata")
                    .waitingFor(
                            Wait.forHttp("/api/v1/ready").forPort(ARCADE_HTTP_PORT).forStatusCode(204));
            arcadedb.start();
            host = arcadedb.getHost();
            port = arcadedb.getMappedPort(ARCADE_HTTP_PORT);
        } else {
            host = "localhost";
            port = ARCADE_HTTP_PORT;
        }

        embeddingStore = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_removal")
                .username("root")
                .password("playwithdata")
                .typeName("EmbeddingDocument")
                .dimension(384)
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
}
