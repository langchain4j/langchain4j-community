package dev.langchain4j.community.neo4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Neo4jEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    private static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");
    private static final String USERNAME = "neo4j";
    private static final String ADMIN_PASSWORD = "adminPass";
    public static final String CUSTOM_LABEL = "TestLabel";

    @Container
    static Neo4jContainer neo4jContainer =
            new Neo4jContainer(DockerImageName.parse("neo4j:" + NEO4J_VERSION)).withAdminPassword(ADMIN_PASSWORD);

    private static Session session;

    @BeforeAll
    static void beforeAll() {
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
        session = driver.session();
        neo4jContainer.start();
    }

    @AfterAll
    static void afterAll() {
        session.close();
        neo4jContainer.stop();
    }

    @AfterEach
    void afterEach() {
        // check entity created
        String queryMatch = String.format("MATCH (n:%s) RETURN n", CUSTOM_LABEL);
        final List<Record> list = session.run(queryMatch).list();
        assertThat(list).hasSize(1);

        // reset database
        String queryDelete = String.format("MATCH (n:%s) DETACH DELETE n", CUSTOM_LABEL);
        session.run(queryDelete);
        session.run("DROP INDEX vector");
    }

    @Test
    void should_respect_embedding_model_bean() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(TestEmbeddingModelAutoConfiguration.class))
                .withPropertyValues(properties())
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel)
                            .isNotNull()
                            .isExactlyInstanceOf(AllMiniLmL6V2QuantizedEmbeddingModel.class);
                    Neo4jEmbeddingStore embeddingStore = context.getBean(Neo4jEmbeddingStore.class);
                    assertThat(embeddingStore).isNotNull();
                    assertThat(embeddingStore.getDimension()).isEqualTo(embeddingModel.dimension());

                    // Just for @BeforeEach
                    TextSegment segment = TextSegment.from("hello");
                    Embedding embedding = embeddingModel.embed(segment.text()).content();
                    String id = embeddingStore.add(embedding, segment);
                    assertThat(id).isNotBlank();
                });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return Neo4jEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return Neo4jEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        return new String[] {
            "langchain4j.community.neo4j.auth.uri=" + neo4jContainer.getBoltUrl(),
            "langchain4j.community.neo4j.auth.user=" + USERNAME,
            "langchain4j.community.neo4j.auth.password=" + ADMIN_PASSWORD,
            "langchain4j.community.neo4j.label=" + CUSTOM_LABEL
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.neo4j.dimension";
    }
}
