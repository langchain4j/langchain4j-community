package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class Neo4jEmbeddingRetrieverIT extends Neo4jEmbeddingRetrieverBaseTest {

    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();


    @Test
    void testRetrieverWithCustomAnswerModelAndPrompt() {
        String promptAnswer = """
            You are an assistant that helps to form nice and human
            understandable answers based on the provided information from tools.
            Do not add any other information that wasn't present in the tools, and use
            very concise style in interpreting results!
            
            Answer like Naruto, saying his typical expression `dattebayo`.
            """;
        
        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(CUSTOM_RETRIEVAL)
                .entityCreationQuery(CUSTOM_CREATION_QUERY)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .driver(driver)
                .query("CREATE (:MainDoc $metadata)")
                .answerModel(chatLanguageModel)
                .promptAnswer(promptAnswer)
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
        DocumentSplitter childSplitter = new DocumentByRegexSplitter(expectedQueryChild, expectedQuery, maxSegmentSize, 0);

        // Index the document into Neo4j as parent-child nodes
        retriever.index(doc, parentSplitter, childSplitter);

        final String retrieveQuery = "Machine Learning";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(1);

        Content result = results.get(0);
        assertTrue(result.textSegment().text().toLowerCase().contains("dattebayo"));
    }
}
