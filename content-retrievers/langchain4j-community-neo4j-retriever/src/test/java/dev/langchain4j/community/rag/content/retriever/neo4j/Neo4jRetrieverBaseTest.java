package dev.langchain4j.community.rag.content.retriever.neo4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;

public class Neo4jRetrieverBaseTest {

    protected static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");

    protected static Driver driver;

    @Container
    protected static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:" + NEO4J_VERSION)
            .withoutAuthentication()
            .withPlugins("apoc");

    @BeforeAll
    static void beforeAll() {
        neo4jContainer.start();
        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void afterAll() {
        driver.close();
        neo4jContainer.stop();
    }

    @AfterEach
    void afterEach() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
    }
}
