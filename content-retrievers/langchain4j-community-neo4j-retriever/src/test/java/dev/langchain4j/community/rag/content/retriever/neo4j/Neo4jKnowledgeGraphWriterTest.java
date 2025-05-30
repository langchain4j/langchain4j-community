package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Neo4jKnowledgeGraphWriterTest extends Neo4jKnowledgeGraphWriterBaseTest {

    @Mock
    private static ChatModel model;

    @Override
    ChatModel getModel() {
        final String keanuStringResponse =
                "[{\"head_type\":\"Person\",\"text\":\"Keanu Reeves acted in Matrix\",\"relation\":\"ACTED_IN\",\"tail_type\":\"Movie\",\"tail\":\"Matrix\",\"head\":\"Keanu Reeves\"}]";
        final ChatResponse chatResponseKeanu = new ChatResponse.Builder()
                .aiMessage(new AiMessage(keanuStringResponse))
                .build();
        final List<ChatMessage> keanuMessages =
                argThat(arg -> arg != null && arg.toString().toLowerCase().contains("keanu"));
        when(model.chat(keanuMessages)).thenReturn(chatResponseKeanu);

        final String sylvesterStringResponse =
                "[{\"tail_type\":\"Location\",\"tail\":\"table\",\"head\":\"Sylvester the cat\",\"head_type\":\"Animal\",\"text\":\"Sylvester the cat is on the table\",\"relation\":\"IS_ON\"}]";
        final ChatResponse chatResponseSylvester = new ChatResponse.Builder()
                .aiMessage(new AiMessage(sylvesterStringResponse))
                .build();
        final List<ChatMessage> sylvesterMessages =
                argThat(arg -> arg != null && arg.toString().toLowerCase().contains("sylvester"));
        when(model.chat(sylvesterMessages)).thenReturn(chatResponseSylvester);

        return model;
    }

    @Test
    void testWrongConstraintName() {
        try {
            knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                    .graph(neo4jGraph)
                    .constraintName("111")
                    .build();
            fail("Should fail due to invalid input");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Error executing query: CREATE CONSTRAINT 111");
        }
    }

    private static final String LABEL_CUSTOM = "Custom";
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    void testKnowledgeGraphWithEmbeddingStoreAndNullEmbeddingModel() {

        final Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_CUSTOM)
                .build();

        try {
            knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                    .graph(neo4jGraph)
                    .embeddingStore(embeddingStore)
                    .build();
            fail("Should fail due to null embeddingModel");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("embeddingModel cannot be null");
        }
    }

    @Test
    void testKnowledgeGraphWithEmbeddingStore() {

        final Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_CUSTOM)
                .build();

        testKnowledgeGraphWithEmbeddingStoreCommon(embeddingStore, false);
    }

    @Test
    void testKnowledgeGraphWithEmbeddingStoreAndIncludeSource() {
        final Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_CUSTOM)
                .build();

        testKnowledgeGraphWithEmbeddingStoreCommon(embeddingStore, true);
    }

    @Test
    void testKnowledgeGraphWithEmbeddingStoreRetrievalQueryAndIncludeSource() {

        final Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_CUSTOM)
                .retrievalQuery(
                        """
                        MATCH (node)<-[r:HAS_ENTITY]-(d:Document)
                        WITH d, collect(DISTINCT {chunk: node, score: score}) AS chunks, avg(score) as avg_score
                        RETURN d.text AS text, avg_score AS score, properties(d) AS metadata
                        ORDER BY score DESC
                        LIMIT $maxResults
                        """)
                .build();

        testKnowledgeGraphWithEmbeddingStoreCommon(embeddingStore, true);
    }

    private static void testKnowledgeGraphWithEmbeddingStoreCommon(
            Neo4jEmbeddingStore embeddingStore, boolean includeSource) {
        final String text = "keanu reeves";
        final Embedding queryEmbedding = EMBEDDING_MODEL.embed(text).content();
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .minScore(0.9)
                .build();
        final List<EmbeddingMatch<TextSegment>> matchesBefore =
                embeddingStore.search(request).matches();
        assertThat(matchesBefore).isEmpty();

        knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                .graph(neo4jGraph)
                .embeddingStore(embeddingStore)
                .embeddingModel(EMBEDDING_MODEL)
                .build();

        knowledgeGraphWriter.addGraphDocuments(graphDocs, includeSource);

        final List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(request).matches();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().text()).containsIgnoringCase(text);
    }
}
