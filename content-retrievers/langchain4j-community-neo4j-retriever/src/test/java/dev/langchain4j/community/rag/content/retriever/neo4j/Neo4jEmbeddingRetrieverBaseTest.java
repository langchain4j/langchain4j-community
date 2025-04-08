package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Neo4jEmbeddingRetrieverBaseTest extends Neo4jRetrieverBaseTest {

    protected static final String CUSTOM_RETRIEVAL = """
            MATCH (node)<-[:REFERS_TO]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

    protected static final String CUSTOM_CREATION_QUERY =
            """
                UNWIND $rows AS row
                MATCH (p:MainDoc {parentId: $parentId})
                CREATE (p)-[:REFERS_TO]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";
    
    protected static Neo4jEmbeddingStore embeddingStore;
    protected static EmbeddingModel embeddingModel;
    
    @BeforeAll
    public static void beforeAll() {
        Neo4jRetrieverBaseTest.beforeAll();
        
        embeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .dimension(384)
                .build();
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    }

    protected static Document getDocumentAI() {
        return Document.from(
                """
                        Artificial Intelligence (AI) is a field of computer science. It focuses on creating intelligent agents capable of performing tasks that require human intelligence.
                                
                        Machine Learning (ML) is a subset of AI. It uses data to learn patterns and make predictions. Deep Learning is a specialized form of ML based on neural networks.
                        """,
                getMetadata()
        );
    }
    
    protected static  Document getDocumentMiscTopics() {
        return Document.from(
                """
                        Quantum mechanics studies how particles behave. It is a fundamental theory in physics.
                                        
                        Gradient descent and backpropagation algorithms.
                                        
                        Spaghetti carbonara and Italian dishes.
                        """,
                getMetadata()
        );
    }

    protected static Metadata getMetadata() {
        return Metadata.from(Map.of("title", "Quantum Mechanics", "source", "Wikipedia link", "url", "https://example.com/ai"));
    }


    protected static void commonResults(List<Content> results, String retrieveQuery) {
        assertThat(results).hasSize(1);

        Content result = results.get(0);

        assertTrue(result.textSegment().text().toLowerCase().contains(retrieveQuery));
        assertEquals("Wikipedia link", result.textSegment().metadata().getString("source"));
        assertEquals("https://example.com/ai", result.textSegment().metadata().getString("url"));
    }

    protected static void seedMainDocAndChildData() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");

            final String text1 = "Gradient descent and backpropagation algorithms";
            final Value embedding1 = Values.value(embeddingModel.embed(text1).content().vector());
            final String text2 = "Spaghetti carbonara and Italian dishes";
            final Value embedding2 = Values.value(embeddingModel.embed(text2).content().vector());
            final String text3 = "Quantum entanglement and uncertainty principles";
            final Value embedding3 = Values.value(embeddingModel.embed(text3).content().vector());

            session.run("""
                CREATE (p1:Document {id: 'p1', text: 'Parent about machine learning', source: 'ml'})
                CREATE (p2:Document {id: 'p2', text: 'Parent about cooking', source: 'food'})
                CREATE (p3:Document {id: 'p3', text: 'Parent about quantum physics', source: 'science'})

                CREATE (c1:Chunk {id: 'c1', text: $text1, embedding: $embedding1})
                CREATE (c2:Chunk {id: 'c2', text: $text2, embedding: $embedding2})
                CREATE (c3:Chunk {id: 'c3', text: $text3, embedding: $embedding3})

                CREATE (p1)-[:REFERS_TO]->(c1)
                CREATE (p2)-[:REFERS_TO]->(c2)
                CREATE (p3)-[:REFERS_TO]->(c3)
            """,
                    Map.of("text1", text1, "embedding1", embedding1,
                            "text2", text2, "embedding2", embedding2,
                            "text3", text3, "embedding3", embedding3));
        }
    }


}
