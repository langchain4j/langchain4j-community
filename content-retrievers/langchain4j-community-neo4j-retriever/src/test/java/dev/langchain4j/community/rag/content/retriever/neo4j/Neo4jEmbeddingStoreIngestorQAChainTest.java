package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.chain.RetrievalQAChain;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStoreIngestor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Neo4jEmbeddingStoreIngestorQAChainTest extends Neo4jEmbeddingStoreIngestorBaseTest {
    @Mock
    private ChatModel chatLanguageModel;

    @Test
    public void testBasicRetrieverWithChatQuestionAndAnswerModel() {

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

        final String chatResponse = "dattebayo";
        when(chatLanguageModel.chat(anyString())).thenReturn(chatResponse).thenReturn(chatResponse);

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

        final Neo4jEmbeddingStoreIngestor build = Neo4jEmbeddingStoreIngestor.builder()
                .driver(driver)
                .query("CREATE (:MainDoc $metadata)")
                .embeddingStore(neo4jEmbeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(splitter)
                .questionModel(chatLanguageModel)
                .userPrompt("mock prompt user")
                .systemPrompt("mock prompt system")
                .build();
        // returns a single result without
        build.ingest(parentDoc);
        final String retrieveQuery = "naruto";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(1);
        final String resultText = results.get(0).textSegment().text();
        assertThat(resultText).doesNotContain(chatResponse);

        PromptTemplate promptTemplate = PromptTemplate.from(
                """
                    Answer the question based only on the context provided.

                    Context:
                    {{contents}}

                    Question:
                    {{userMessage}}

                    Answer:
                    """);

        // chain with prompt builder
        RetrievalQAChain chain = RetrievalQAChain.builder()
                .chatModel(chatLanguageModel)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(retriever)
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(promptTemplate)
                                .build())
                        .build())
                .build();

        final String chainResult = chain.execute(Query.from(retrieveQuery));
        assertThat(chainResult).containsIgnoringCase(chatResponse);

        // chain with prompt builder
        RetrievalQAChain chainWithPromptBuilder = RetrievalQAChain.builder()
                .chatModel(chatLanguageModel)
                .contentRetriever(retriever)
                .prompt(promptTemplate)
                .build();

        final String chainResultWithPromptBuilder = chainWithPromptBuilder.execute(Query.from(retrieveQuery));
        assertThat(chainResultWithPromptBuilder).containsIgnoringCase(chatResponse);
    }
}
