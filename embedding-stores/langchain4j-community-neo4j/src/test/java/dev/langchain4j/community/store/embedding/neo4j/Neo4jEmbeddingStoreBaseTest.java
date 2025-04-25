package dev.langchain4j.community.store.embedding.neo4j;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class Neo4jEmbeddingStoreBaseTest extends EmbeddingStoreIT {

    protected static final String USERNAME = "neo4j";
    protected static final String ADMIN_PASSWORD = "adminPass";
    protected static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";
    protected static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26-enterprise");

    @Container
    protected static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(
                    DockerImageName.parse("neo4j:" + NEO4J_VERSION))
            .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
            .withAdminPassword(ADMIN_PASSWORD);

    protected static final String METADATA_KEY = "test-key";

    protected Neo4jEmbeddingStore embeddingStore;

    protected final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    protected static Session session;

    @BeforeAll
    static void beforeAll() {
        neo4jContainer.start();
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
        session = driver.session();
    }

    @AfterAll
    static void afterAll() {
        session.close();
        neo4jContainer.stop();
    }

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
        session.executeWriteWithoutResult(
                tx -> tx.run("MATCH (n) DETACH DELETE n").consume());
        session.run("CALL db.awaitIndexes()");

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_TO_SANITIZE)
                .build();
    }
}
