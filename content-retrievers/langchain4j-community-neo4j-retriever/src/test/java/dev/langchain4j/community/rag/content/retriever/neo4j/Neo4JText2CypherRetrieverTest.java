package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private ChatLanguageModel chatLanguageModel;

    @BeforeEach
    void beforeEach() {
        super.beforeEach();

        retriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValid() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.chat(anyString()))
                .thenReturn("MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidWithDeprecatedClass() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.chat(anyString()))
                .thenReturn("MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        Neo4jContentRetriever retriever = Neo4jContentRetriever.builder()
                .graph(graph)
                .chatLanguageModel(chatLanguageModel)
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
        when(chatLanguageModel.chat(anyString()))
                .thenReturn(
                        "```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldReturnArithmeticException() {
        try {
            Neo4jGraph.builder().driver(driver).sample(0L).maxRels(0L).build();
            fail("Should fail due to ArithmeticException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("java.lang.ArithmeticException: / by zero");
        }
    }

    @Test
    void shouldReturnEmptyListWhenQueryIsInvalid() {
        // Given
        Query query = new Query("Who is the author of the movie 'Dune'?");
        when(chatLanguageModel.chat(anyString()))
                .thenReturn(
                        "MATCH(movie:Movie {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).isEmpty();
    }

    @Test
    void shouldThrowsErrorWhenNeo4jGraphQueryIsInvalid() {
        final String invalidQuery = "MATCH(movie:Movie {title: 'Dune'}) RETURN author.name AS output";

        try {
            graph.executeRead(invalidQuery);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Variable `author` not defined");
        }

        try {
            graph.executeWrite(invalidQuery);
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("Variable `author` not defined");
        }
    }

    @Test
    void shouldRetrieveContentWithExample() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.chat((String) argThat(arg -> {
                    final String argString = (String) arg;
                    return argString != null && argString.contains("Cypher examples:");
                })))
                .thenReturn(
                        "```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```");

        when(chatLanguageModel.chat((String) argThat(arg -> {
                    final String argString = (String) arg;
                    return argString != null && !argString.contains("Cypher examples:");
                })))
                .thenReturn("```MATCH(author:NotExisting) RETURN author.name AS output```");

        // When
        final Neo4jText2CypherRetriever retrieverWithoutExamples = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatLanguageModel(chatLanguageModel)
                .build();

        List<Content> contentsWithoutExamples = retrieverWithoutExamples.retrieve(query);

        // Then
        assertThat(contentsWithoutExamples).isEmpty();

        // When
        final Neo4jText2CypherRetriever retrieverWithExamples = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatLanguageModel(chatLanguageModel)
                .examples(List.of("Mock cypher examples.."))
                .build();

        List<Content> contentsWithExamples = retrieverWithExamples.retrieve(query);

        // Then
        assertThat(contentsWithExamples).hasSize(1);
    }
}
