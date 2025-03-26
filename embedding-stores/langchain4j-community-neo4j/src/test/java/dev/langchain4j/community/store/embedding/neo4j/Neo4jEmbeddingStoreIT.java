package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
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

        final String label = "Elisabeth";
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("elisabeth_vector")
                .fullTextIndexName("elisabeth_text")
                .fullTextQuery("Matrix")
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
            final String s = "CREATE FULLTEXT INDEX elisabeth_text IF NOT EXISTS FOR (e:%s) ON EACH [e.%s]"
                    .formatted(label, DEFAULT_ID_PROP);
            tx.run(s).consume();
            return null;
        });

        final List<TextSegment> split = new DocumentByParagraphSplitter(20, 10).split(textDocument);

        List<Embedding> embeddings = embeddingModel.embedAll(split).content();
        embeddingStore.addAll(embeddings, split);

        final Embedding queryEmbedding = embeddingModel.embed("Elisabeth I").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        final List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(embeddingSearchRequest).matches();
        matches.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains("Elizabeth");
        });

        String wikiContent = textDocument.text().split("Signature ")[1];
        wikiContent = wikiContent.substring(0, 5000);

        final String userMessage = String.format(
                """
                        Can you transform the following text into Cypher statements using both nodes and relationships?
                        Each node and relation should have a single property "id",\s
                        and each node has an additional label named __Entity__
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
        final String generate = openAiChatModel.chat(userMessage);

        for (String query : generate.split(";")) {
            session.executeWrite(tx -> {
                tx.run(query).consume();
                return null;
            });
        }

        final List<EmbeddingMatch<TextSegment>> matchesWithFullText =
                embeddingStore.search(embeddingSearchRequest).matches();
        assertThat(matchesWithFullText).hasSize(3);
        matchesWithFullText.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains("Elizabeth");
        });
    }
}
