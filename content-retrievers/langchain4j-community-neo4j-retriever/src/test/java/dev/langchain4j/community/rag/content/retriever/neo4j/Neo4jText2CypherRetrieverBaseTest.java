package dev.langchain4j.community.rag.content.retriever.neo4j;

import org.junit.jupiter.api.BeforeEach;
import org.neo4j.driver.Session;

public class Neo4jText2CypherRetrieverBaseTest extends Neo4jContainerBaseTest {
    protected Neo4jGraph graph;

    @BeforeEach
    void beforeEach() {
        try (Session session = driver.session()) {
            session.run("CREATE (book:Book {title: 'Dune'})<-[:WROTE]-(author:Person {name: 'Frank Herbert'})");
        }
        graph = Neo4jGraph.builder().driver(driver).build();
    }
}
