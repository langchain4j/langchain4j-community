package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.store.embedding.ParentChildEmbeddingStoreIngestor;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStoreIngestor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Neo4jEmbeddingStoreIngestorTest extends Neo4jEmbeddingStoreIngestorBaseTest {

    @Mock
    private ChatModel chatLanguageModel;

    @Test
    public void testBasicRetriever() {
        Document parentDoc = getDocumentMiscTopics();

        // Child splitter: splits into sentences using OpenNLP
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter splitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        final ParentChildEmbeddingStoreIngestor ingestor = ParentChildEmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        ingestor.ingest(parentDoc);

        // Query and validate results
        final String retrieveQuery = "fundamental theory";
        final EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(1)
                .minScore(0.4)
                .build();
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        commonResults(results, retrieveQuery);
    }

    @Test
    public void testRetrieverWithChatModel() {

        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(CUSTOM_RETRIEVAL)
                .entityCreationQuery(CUSTOM_CREATION_QUERY)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        when(chatLanguageModel.chat(anyList()))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.aiMessage("Naruto"))
                        .build());

        final EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(neo4jEmbeddingStore)
                .maxResults(2)
                .minScore(0.4)
                .build();

        Document parentDoc = getDocumentMiscTopics();

        // Child splitter: splits into sentences using OpenNLP
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter splitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        final Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingStore(neo4jEmbeddingStore)
                .embeddingModel(embeddingModel)
                .driver(driver)
                .query("CREATE (:MainDoc $metadata)")
                .questionModel(chatLanguageModel)
                .userPrompt("mock prompt user")
                .systemPrompt("mock prompt system")
                .build();
        ingestor.ingest(parentDoc);
        final String retrieveQuery = "naruto";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        commonResults(results, retrieveQuery);
    }

    @Test
    void testRetrieverWithCustomRetrievalAndEmbeddingCreationQuery() {

        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(CUSTOM_RETRIEVAL)
                .entityCreationQuery(CUSTOM_CREATION_QUERY)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        final EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.4)
                .embeddingStore(neo4jEmbeddingStore)
                .build();

        Document doc = getDocumentAI();

        // MainDoc splitter splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter splits on periods (sentences)
        final String expectedQueryChild = "\\. ";
        DocumentSplitter childSplitter = new DocumentByRegexSplitter(expectedQueryChild, expectedQuery, 150, 0);

        final Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
                .documentSplitter(parentSplitter)
                .documentChildSplitter(childSplitter)
                .driver(driver)
                .query("CREATE (:MainDoc $metadata)")
                .embeddingStore(neo4jEmbeddingStore)
                .embeddingModel(embeddingModel)
                .build();
        // Index the document into Neo4j as parent-child nodes
        ingestor.ingest(doc);

        final String retrieveQuery = "Machine Learning";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(1);
    }

    // TODO - change with cypher-dsl
    @Test
    void testRetrieverWithCustomRetrievalAndEmbeddingCreationQueryMainDocIdAndParams() {
        String customCreationQuery =
                """
                UNWIND $rows AS row
                MATCH (p:MainDoc {customParentId: $customParentId})
                CREATE (p)-[:REFERS_TO]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";
        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(CUSTOM_RETRIEVAL)
                .entityCreationQuery(customCreationQuery)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        final EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.4)
                .embeddingStore(neo4jEmbeddingStore)
                .build();

        Document doc = getDocumentAI();

        // MainDoc splitter splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter splits on periods (sentences)
        final String expectedQueryChild = "\\. ";
        DocumentSplitter childSplitter =
                new DocumentByRegexSplitter(expectedQueryChild, expectedQuery, maxSegmentSize, 0);

        final Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
                .documentSplitter(parentSplitter)
                .documentChildSplitter(childSplitter)
                .driver(driver)
                .query("CREATE (:MainDoc $metadata)")
                .parentIdKey("customParentId")
                .params(Map.of("customMainDocId", "foo", "bar", 1))
                .embeddingStore(neo4jEmbeddingStore)
                .embeddingModel(embeddingModel)
                .build();
        // Index the document into Neo4j as parent-child nodes
        ingestor.ingest(doc);

        final String retrieveQuery = "Machine Learning";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(1);
    }

    @Test
    void testRetrieverWithCustomRetrievalAndEmbeddingCreationQueryAndPreInsertedData() {

        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(CUSTOM_RETRIEVAL)
                .entityCreationQuery(CUSTOM_CREATION_QUERY)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        seedMainDocAndChildData();

        final EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.6)
                .embeddingStore(neo4jEmbeddingStore)
                .build();

        // Act
        List<Content> results = retriever.retrieve(new Query("quantum physics"));

        // Assert
        assertEquals(1, results.size());
        Content parent = results.get(0);

        assertTrue(parent.textSegment().text().contains("quantum physics"));
        assertEquals("science", parent.textSegment().metadata().getString("source"));
    }
}
