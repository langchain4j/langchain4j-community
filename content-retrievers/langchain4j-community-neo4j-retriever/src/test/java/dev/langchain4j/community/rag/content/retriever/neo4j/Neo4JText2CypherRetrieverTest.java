package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Neo4JText2CypherRetrieverTest extends Neo4jText2CypherRetrieverBaseTest {

    private Neo4jText2CypherRetriever retriever;

    @Mock
    private ChatModel chatModel;

    @BeforeEach
    void beforeEach() {
        super.beforeEach();

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .build();
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValid() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldReturnsEmptyIfMaxRetriesIsNegative() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse("MATCH (n:Something) RETURN n"))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .maxRetries(-1)
                .build();

        // When
        List<Content> contentsWithCypherDSL = retriever.retrieve(query);

        // Then
        assertThat(contentsWithCypherDSL).isEmpty();
    }

    @Test
    void shouldReturnsEmptyIfICallWithoutRetry() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");

        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse("MATCH (n:Something) RETURN n"))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .maxRetries(1)
                .build();

        // When
        List<Content> contentsWithCypherDSL = retriever.retrieve(query);

        // Then
        assertThat(contentsWithCypherDSL).isEmpty();
    }

    @Test
    void shouldRetrieveResultIfICallWithRetryAndIfTheFirstTryIsEmpty() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse("MATCH (n:Something) RETURN n"))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .build();

        // When
        List<Content> contentsWithCypherDSL = retriever.retrieve(query);

        // Then
        assertThat(contentsWithCypherDSL).hasSize(1);
    }

    @Test
    void shouldThrowsErrorIfICallWithoutRetry() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenThrow(new RuntimeException("Invalid statement"))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .maxRetries(1)
                .build();

        try {
            retriever.retrieve(query);
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Invalid statement");
        }
    }

    @Test
    void shouldRetrieveResultIfICallWithRetryAndIfTheFirstTryHasError() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenThrow(new RuntimeException("Invalid statement"))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .build();

        // When
        List<Content> contentsWithCypherDSL = retriever.retrieve(query);

        // Then
        assertThat(contentsWithCypherDSL).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidWithDeprecatedClass() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse(
                        "MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .build();

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndResponseHasBackticks() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(
                        getChatResponse(
                                "```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```"));

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenQueryHasNoResults() {
        // Given
        Query query = new Query("Who is the author of the movie 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse(
                        "MATCH(movie:Movie {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output"));

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenCypherQueryIsInvalid() {
        // Given
        Query query = new Query("Who is the author of the movie 'Dune'?");
        when(chatModel.chat(anyList()))
                .thenReturn(getChatResponse("MATCH(movie:Movie {title: 'Dune'}) RETURN author.name AS output"));

        try {
            retriever.retrieve(query);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Variable `author` not defined");
        }
    }

    private static ChatResponse getChatResponse(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    @Test
    void shouldRetrieveContentWithExample() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatModel.chat((List<ChatMessage>) argThat(arg -> {
                    final List<ChatMessage> argString = (List<ChatMessage>) arg;
                    return argString != null
                            && argString.stream()
                                    .anyMatch(
                                            i -> ((UserMessage) i).singleText().contains("Cypher examples:"));
                })))
                .thenReturn(
                        getChatResponse(
                                "```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```"));

        when(chatModel.chat((List<ChatMessage>) argThat(arg -> {
                    final List<ChatMessage> argString = (List<ChatMessage>) arg;
                    return argString != null
                            && argString.stream()
                                    .noneMatch(
                                            i -> ((UserMessage) i).singleText().contains("Cypher examples:"));
                })))
                .thenReturn(getChatResponse("```MATCH(author:NotExisting) RETURN author.name AS output```"));

        // When
        final Neo4jText2CypherRetriever retrieverWithoutExamples = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .build();

        List<Content> contentsWithoutExamples = retrieverWithoutExamples.retrieve(query);

        // Then
        assertThat(contentsWithoutExamples).isEmpty();

        // When
        final Neo4jText2CypherRetriever retrieverWithExamples = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(chatModel)
                .examples(List.of("Mock cypher examples.."))
                .build();

        List<Content> contentsWithExamples = retrieverWithExamples.retrieve(query);

        // Then
        assertThat(contentsWithExamples).hasSize(1);
    }
}
