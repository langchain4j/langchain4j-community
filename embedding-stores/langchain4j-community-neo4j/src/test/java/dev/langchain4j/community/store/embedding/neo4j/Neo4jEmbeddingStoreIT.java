package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.selenium.SeleniumDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BrowserWebDriverContainer;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class Neo4jEmbeddingStoreIT extends Neo4jEmbeddingStoreBaseTest {

    // Emulating as far as possible the langchain (python) use case
    // https://neo4j.com/developer-blog/enhance-rag-knowledge-graph/
    @Test
    void should_emulate_issue_1306_case() {

        final String label = "Entity";
        final String retrievalQuery = String.format(
                "RETURN properties(node) AS metadata, node.%1$s AS %1$s, node.%2$s AS %2$s, node.%3$s AS %3$s, score",
                DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, DEFAULT_EMBEDDING_PROP);
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("elisabeth_vector")
                .fullTextIndexName("elizabeth_text")
                .fullTextQuery("elizabeth*")
                // create a `retrievalQuery` which returns empty result (but with the same column as the fulltext one)
                // and a `fullTextRetrievalQuery` returning results
                // so that we know that the result are all coming from full-text query
                .retrievalQuery(" AND node.nonexistent IS NOT NULL " + retrievalQuery)
                .fullTextRetrievalQuery(retrievalQuery)
                .build();

        DocumentParser parser = new TextDocumentParser();
        HtmlToTextDocumentTransformer extractor = new HtmlToTextDocumentTransformer();
        BrowserWebDriverContainer<?> chromeContainer =
                new BrowserWebDriverContainer<>().withCapabilities(new ChromeOptions());
        chromeContainer.start();
        RemoteWebDriver webDriver = new RemoteWebDriver(chromeContainer.getSeleniumAddress(), new ChromeOptions());
        SeleniumDocumentLoader loader = SeleniumDocumentLoader.builder()
                .webDriver(webDriver)
                .timeout(Duration.ofSeconds(30))
                .build();
        String url = "https://en.wikipedia.org/wiki/Elizabeth_I";
        Document document = loader.load(url, parser);
        Document textDocument = extractor.transform(document);

        session.executeWrite(tx -> {
            final String query = String.format(
                    "CREATE FULLTEXT INDEX elizabeth_text IF NOT EXISTS FOR (e:%s) ON EACH [e.%s]",
                    label, DEFAULT_ID_PROP);
            tx.run(query).consume();
            return null;
        });

        final List<TextSegment> split = new DocumentByParagraphSplitter(20, 10).split(textDocument);

        List<Embedding> embeddings = embeddingModel.embedAll(split).content();
        embeddingStore.addAll(embeddings, split);

        final Embedding queryEmbedding = embeddingModel.embed("Elizabeth I").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        final List<EmbeddingMatch<TextSegment>> matchesWithoutFullText =
                embeddingStore.search(embeddingSearchRequest).matches();
        // this is empty because of `node.nonexistent IS NOT NULL ` in the retrieval query
        assertThat(matchesWithoutFullText).isEmpty();

        String wikiContent = textDocument.text().split("Signature ")[1];
        wikiContent = wikiContent.substring(0, 5000);

        final String userMessage = String.format(
                """
                        Can you transform the following text into Cypher statements using both nodes and relationships?
                        Each node and relation should have a single property "id",\s
                        and each node has an additional label named Entity
                        The id property values should have whitespace instead of _ or other special characters.
                        Just returns an unique query non ; separated,
                        without the ``` wrapping.
                        ```
                        %s
                        ```
                        """,
                wikiContent);

        final OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        // re-execute the Cypher statements in case of errors
        withRetry(
                () -> {
                    session.executeWrite(
                            tx -> tx.run("MATCH (n) DETACH DELETE n").consume());

                    final String generate = openAiChatModel.chat(userMessage);

                    for (String query : generate.split(";")) {
                        session.executeWrite(tx -> {
                            tx.run(query).consume();
                            return null;
                        });
                    }
                    return null;
                },
                3);
        final List<EmbeddingMatch<TextSegment>> matchesWithFullText =
                embeddingStore.search(embeddingSearchRequest).matches();
        // besides `Elizabeth I`, there could be other similar nodes like `Elizabethan era`, `Elizabeth of York`,
        // `Elizabethan Religious Settlement`, ...
        assertThat(matchesWithFullText).hasSizeGreaterThanOrEqualTo(1);
        matchesWithFullText.forEach(i -> assertThat(i.embeddingId()).contains("Elizabeth"));
    }

    @Test
    void should_throw_error_if_another_index_name_with_different_label_exists() {
        String metadataPrefix = "metadata.";
        String idxName = "WillFail";

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .indexName(idxName)
                .metadataPrefix(metadataPrefix)
                .awaitIndexTimeout(20)
                .build();

        String secondLabel = "Second label";
        try {
            embeddingStore = Neo4jEmbeddingStore.builder()
                    .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                    .dimension(384)
                    .label(secondLabel)
                    .indexName(idxName)
                    .metadataPrefix(metadataPrefix)
                    .build();
            fail("Should fail due to idx conflict");
        } catch (RuntimeException e) {
            String errMsg = String.format(
                    "It's not possible to create an index for the label `%s` and the property `%s`",
                    secondLabel, DEFAULT_EMBEDDING_PROP);
            assertThat(e.getMessage()).contains(errMsg);
        }
    }
}
