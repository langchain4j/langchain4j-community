package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class Neo4jText2CypherRetrieverIT extends Neo4jText2CypherRetrieverBaseTest {

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsed() {

        // With
        ChatModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(openAiChatModel)
                .build();

        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");

        // When
        List<Content> contents = neo4jContentRetriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsedWithExample() throws Exception {
        // remove existing `Book` and `Person` entities
        driver.session().run("MATCH (n) DETACH DELETE n");
        // recreate Neo4jGraph instead of reuse `this.graph`, otherwise it remains the old one with (:Book), etc..
        final Neo4jGraph graphStreamer = Neo4jGraph.builder().driver(driver).build();

        URI resource = getClass()
                .getClassLoader()
                .getResource("streamer_dataset.cypher")
                .toURI();
        String datasetEntities = Files.readString(Paths.get(resource));

        driver.session().executeWriteWithoutResult(tx -> {
            for (String query : datasetEntities.split(";")) {
                System.out.println("query = " + query);
                tx.run(query);
            }
        });

        // With
        ChatModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        List<String> examples = List.of(
                """
            # Which streamer has the most followers?
            MATCH (s:Stream)
            RETURN s.name AS streamer
            ORDER BY s.followers DESC LIMIT 1
            """,
                """
            # How many streamers are from Norway?
            MATCH (s:Stream)-[:HAS_LANGUAGE]->(:Language {{name: 'Norwegian'}})
            RETURN count(s) AS streamers
            Note: Do not include any explanations or apologies in your responses.
            Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
            Do not include any text except the generated Cypher statement.
            """);
        final String textQuery = "Which streamer from Italy has the most followers?";
        Query query = new Query(textQuery);

        Neo4jText2CypherRetriever neo4jContentRetrieverWithoutExample = Neo4jText2CypherRetriever.builder()
                .graph(graphStreamer)
                .chatModel(openAiChatModel)
                .build();
        List<Content> contentsWithoutExample = neo4jContentRetrieverWithoutExample.retrieve(query);
        assertThat(contentsWithoutExample).isEmpty();

        // When
        Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
                .graph(graphStreamer)
                .chatModel(openAiChatModel)
                .examples(examples)
                .build();

        // Then
        // retry mechanism since the result is not deterministic
        final String text = RetryUtils.withRetry(
                () -> {
                    List<Content> contents = neo4jContentRetriever.retrieve(query);
                    assertThat(contents).hasSize(1);
                    return contents.get(0).textSegment().text();
                },
                5);

        // check validity of the response
        final String name = driver.session()
                .run(
                        "MATCH (s:Stream)-[:HAS_LANGUAGE]->(l:Language {name: 'Italian'}) RETURN s.name ORDER BY s.followers DESC LIMIT 1")
                .single()
                .values()
                .get(0)
                .toString();
        assertThat(text).isEqualTo(name);
    }
}
