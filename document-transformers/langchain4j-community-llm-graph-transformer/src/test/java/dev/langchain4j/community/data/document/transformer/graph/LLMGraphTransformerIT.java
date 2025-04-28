package dev.langchain4j.community.data.document.transformer.graph;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class LLMGraphTransformerIT {

    private static final String EXAMPLES_PROMPT =
            """
                    [
                       {
                          "tail":"Microsoft",
                          "head":"Adam",
                          "head_type":"Person",
                          "text":"Adam is a software engineer in Microsoft since 2009, and last year he got an award as the Best Talent",
                          "relation":"WORKS_FOR",
                          "tail_type":"Company"
                       },
                       {
                          "tail":"Best Talent",
                          "head":"Adam",
                          "head_type":"Person",
                          "text":"Adam is a software engineer in Microsoft since 2009, and last year he got an award as the Best Talent",
                          "relation":"HAS_AWARD",
                          "tail_type":"Award"
                       },
                       {
                          "tail":"Microsoft",
                          "head":"Microsoft Word",
                          "head_type":"Product",
                          "text":"Microsoft is a tech company that provide several products such as Microsoft Word",
                          "relation":"PRODUCED_BY",
                          "tail_type":"Company"
                       },
                       {
                          "tail":"lightweight app",
                          "head":"Microsoft Word",
                          "head_type":"Product",
                          "text":"Microsoft Word is a lightweight app that accessible offline",
                          "relation":"HAS_CHARACTERISTIC",
                          "tail_type":"Characteristic"
                       },
                       {
                          "tail":"accessible offline",
                          "head":"Microsoft Word",
                          "head_type":"Product",
                          "text":"Microsoft Word is a lightweight app that accessible offline",
                          "relation":"HAS_CHARACTERISTIC",
                          "tail_type":"Characteristic"
                       }
                    ]
                    """;

    private static ChatModel model;

    @BeforeAll
    static void beforeAll() {
        model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    void testAddGraphDocumentsWithMissingModel() {
        try {
            LLMGraphTransformer.builder().build();
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("chatModel cannot be null");
        }
    }

    @Test
    void testAddGraphDocumentsWithMissingExamples() {
        try {
            LLMGraphTransformer.builder().model(model).build();
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("examples cannot be null");
        }
    }

    @Test
    void testAddGraphDocumentsWithCustomPrompt() {
        List<ChatMessage> prompt =
                List.of(new UserMessage("just return a null value, don't add any explanation or extra text."));

        LLMGraphTransformer transformer = LLMGraphTransformer.builder()
                .model(model)
                .examples(EXAMPLES_PROMPT)
                .prompt(prompt)
                .build();

        Document doc3 = new DefaultDocument(
                "Keanu Reeves acted in Matrix. Keanu was born in Beirut", Metadata.from("key3", "value3"));
        List<GraphDocument> documents = transformer.transformAll(List.of(doc3));
        assertThat(documents).isEmpty();
    }

    @Test
    void testAddGraphDocumentsWithCustomNodesAndRelationshipsSchema() {
        String cat = "Sylvester the cat";
        String keanu = "Keanu Reeves";
        String lino = "Lino Banfi";
        String goku = "Goku";
        String hajime = "Hajime Isayama";
        String levi = "Levi";
        String table = "table";
        String matrix = "Matrix";
        String vac = "Vieni Avanti Cretino";
        String db = "Dragon Ball";
        String aot = "Attack On Titan";

        Document docCat = Document.from("%s is on the %s".formatted(cat, table));
        Document docKeanu = Document.from("%s acted in %s".formatted(keanu, matrix));
        Document docLino = Document.from("%s acted in %s".formatted(lino, vac));
        Document docGoku = Document.from("%s acted in %s".formatted(goku, db));
        Document docHajime = Document.from("%s wrote %s. %s acted in %s".formatted(hajime, aot, levi, aot));

        List<Document> docs = List.of(docCat, docKeanu, docLino, docGoku, docHajime);

        LLMGraphTransformer build2 = LLMGraphTransformer.builder()
                .model(model)
                .examples(EXAMPLES_PROMPT)
                .build();
        List<GraphDocument> documents2 = build2.transformAll(docs);
        Stream<String> expectedNodes = Stream.of(cat, keanu, lino, goku, hajime, levi, table, matrix, vac, db, aot);
        assertThat(documents2).hasSize(5);
        graphDocsAssertions(documents2, expectedNodes, Stream.of("acted", "acted", "acted", "acted", "wr.", "on"));

        LLMGraphTransformer transformer = LLMGraphTransformer.builder()
                .model(model)
                .examples(EXAMPLES_PROMPT)
                .allowedNodes(List.of("Person"))
                .allowedRelationships(List.of("Acted_in"))
                .build();

        List<GraphDocument> documents = transformer.transformAll(docs);
        System.out.println("documents = " + documents);
        assertThat(documents).hasSize(4);
        String[] strings = {keanu, lino, goku, levi, matrix, vac, db, aot};
        graphDocsAssertions(documents, Stream.of(strings), Stream.of("acted", "acted", "acted", "acted"));

        LLMGraphTransformer build3 = LLMGraphTransformer.builder()
                .model(model)
                .examples(EXAMPLES_PROMPT)
                .allowedNodes(List.of("Person"))
                .allowedRelationships(List.of("Writes", "Acted_in"))
                .build();

        List<GraphDocument> documents3 = build3.transformAll(docs);
        assertThat(documents).hasSize(4);
        String[] elements3 = {keanu, lino, goku, hajime, levi, matrix, vac, db, aot};

        graphDocsAssertions(documents3, Stream.of(elements3), Stream.of("acted", "acted", "acted", "acted", "wr."));
    }

    @Test
    void testAddGraphDocumentsWithDeDuplication() {
        LLMGraphTransformer transformer = LLMGraphTransformer.builder()
                .model(model)
                .examples(EXAMPLES_PROMPT)
                .build();

        Document doc3 = new DefaultDocument(
                "Keanu Reeves acted in Matrix. Keanu was born in Beirut", Metadata.from("key3", "value3"));

        List<Document> documents = List.of(doc3);
        List<GraphDocument> graphDocs = transformer.transformAll(documents);

        assertThat(graphDocs).hasSize(1);
        Stream<String> expectedNodeElements = Stream.of("matrix", "keanu", "beirut");
        Stream<String> expectedEdgeElements = Stream.of("acted", "born");
        graphDocsAssertions(graphDocs, expectedNodeElements, expectedEdgeElements);
    }

    private static void graphDocsAssertions(
            List<GraphDocument> documents, Stream<String> expectedNodeElements, Stream<String> expectedEdgeElements) {
        List<String> actualNodes = getNodeIds(documents);
        List<String> actualRelationships = getRelationshipIds(documents);
        entitiesAssertions(expectedNodeElements, actualNodes);

        entitiesAssertions(expectedEdgeElements, actualRelationships);
    }

    private static void entitiesAssertions(Stream<String> expectedNodeElements, List<String> actualNodes) {
        List<String> expectedNodes = expectedNodeElements.sorted().toList();
        assertThat(actualNodes).hasSameSizeAs(expectedNodes);
        for (int i = 0; i < actualNodes.size(); i++) {
            assertThat(actualNodes.get(i).toLowerCase()).containsPattern("(?i)" + expectedNodes.get(i));
        }
    }

    private static List<String> getNodeIds(List<GraphDocument> documents2) {
        return documents2.stream()
                .flatMap(i -> i.nodes().stream().map(GraphNode::id))
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> getRelationshipIds(List<GraphDocument> documents2) {
        return documents2.stream()
                .flatMap(i -> i.relationships().stream().map(GraphEdge::type))
                .sorted()
                .collect(Collectors.toList());
    }
}
