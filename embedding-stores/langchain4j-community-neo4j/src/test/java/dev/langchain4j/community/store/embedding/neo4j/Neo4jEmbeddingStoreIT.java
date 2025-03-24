package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

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
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
            final String s = "CREATE FULLTEXT INDEX elizabeth_text IF NOT EXISTS FOR (e:%s) ON EACH [e.%s]"
                    .formatted(label, DEFAULT_ID_PROP);
            tx.run(s).consume();
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
        final List<EmbeddingMatch<TextSegment>> relevant =
                embeddingStore.search(request).matches();
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);

        checkEntitiesCreated(relevant.size(), iterator -> {
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node);
                }
            });
        });
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

    @Test
    void row_batches_single_element() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(1);
        assertThat(rowsBatched).hasSize(1);
        assertThat(rowsBatched.get(0)).hasSize(1);
    }

    @Test
    void row_batches_10000_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(10000);
        assertThat(rowsBatched).hasSize(1);
        assertThat(rowsBatched.get(0)).hasSize(10000);
    }

    @Test
    void row_batches_20000_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(20000);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(10000);
    }

    @Test
    void row_batches_11001_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(11001);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(1001);
    }

    @Test
    void should_add_embedding_with_id_and_retrieve_with_and_without_prefilter() {

        final List<TextSegment> segments = IntStream.range(0, 10)
                .boxed()
                .map(i -> {
                    if (i == 0) {
                        final Map<String, Object> metas =
                                Map.of("key1", "value1", "key2", 10, "key3", "3", "key4", "value4");
                        final Metadata metadata = new Metadata(metas);
                        return TextSegment.from(randomUUID(), metadata);
                    }
                    return TextSegment.from(randomUUID());
                })
                .toList();

        final List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        embeddingStore.addAll(embeddings, segments);

        final And filter = new And(
                new And(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "10")),
                new Not(new Or(new IsIn("key3", asList("1", "2")), new IsNotEqualTo("key4", "value4"))));

        TextSegment segmentToSearch = TextSegment.from(randomUUID());
        Embedding embeddingToSearch =
                embeddingModel.embed(segmentToSearch.text()).content();
        final EmbeddingSearchRequest requestWithFilter = EmbeddingSearchRequest.builder()
                .maxResults(5)
                .minScore(0.0)
                .filter(filter)
                .queryEmbedding(embeddingToSearch)
                .build();
        final EmbeddingSearchResult<TextSegment> searchWithFilter = embeddingStore.search(requestWithFilter);
        final List<EmbeddingMatch<TextSegment>> matchesWithFilter = searchWithFilter.matches();
        assertThat(matchesWithFilter).hasSize(1);

        final EmbeddingSearchRequest requestWithoutFilter = EmbeddingSearchRequest.builder()
                .maxResults(5)
                .minScore(0.0)
                .queryEmbedding(embeddingToSearch)
                .build();
        final EmbeddingSearchResult<TextSegment> searchWithoutFilter = embeddingStore.search(requestWithoutFilter);
        final List<EmbeddingMatch<TextSegment>> matchesWithoutFilter = searchWithoutFilter.matches();
        assertThat(matchesWithoutFilter).hasSize(5);
    }

    private List<List<Map<String, Object>>> getListRowsBatched(int numElements) {
        List<TextSegment> embedded = IntStream.range(0, numElements)
                .mapToObj(i -> TextSegment.from("text-" + i))
                .toList();
        List<String> ids =
                IntStream.range(0, numElements).mapToObj(i -> "id-" + i).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(embedded).content();

        return Neo4jEmbeddingUtils.getRowsBatched((Neo4jEmbeddingStore) embeddingStore, ids, embeddings, embedded)
                .toList();
    }

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
}
