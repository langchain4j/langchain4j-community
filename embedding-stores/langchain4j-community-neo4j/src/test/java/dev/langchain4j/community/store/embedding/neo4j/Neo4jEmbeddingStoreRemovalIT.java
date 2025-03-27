package dev.langchain4j.community.store.embedding.neo4j;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class Neo4jEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {
    private static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");
    private static final String USERNAME = "neo4j";
    private static final String ADMIN_PASSWORD = "adminPass";
    private static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";

    @Container
    static Neo4jContainer<?> neo4jContainer =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:" + NEO4J_VERSION)).withAdminPassword(ADMIN_PASSWORD);

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    EmbeddingStore<TextSegment> embeddingStore = Neo4jEmbeddingStore.builder()
            .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
            .dimension(384)
            .label(LABEL_TO_SANITIZE)
            .build();

    @AfterEach
    void afterEach() {
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
