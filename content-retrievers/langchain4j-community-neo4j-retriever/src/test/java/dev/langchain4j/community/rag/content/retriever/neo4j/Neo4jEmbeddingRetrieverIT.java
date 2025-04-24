package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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

        Document doc = getDocumentMiscTopics();

        // MainDoc splitter splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter splits on periods (sentences)
        final String expectedQueryChild = "\\. ";
        DocumentSplitter childSplitter = new DocumentByRegexSplitter(expectedQueryChild, expectedQuery, maxSegmentSize, 0);

        // Index the document into Neo4j as parent-child nodes
        retriever.index(doc, parentSplitter, childSplitter);

        final String retrieveQuery = "Who is John Doe?";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(1);

        Content result = results.get(0);
        assertThat(result.textSegment().text()).containsIgnoringCase("dattebayo");
        assertThat(result.textSegment().text()).containsIgnoringCase("super saiyan");
    }
}
