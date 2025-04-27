package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.community.rag.content.retriever.neo4j.KnowledgeGraphWriter.DEFAULT_ID_PROP;
import static dev.langchain4j.community.rag.content.retriever.neo4j.KnowledgeGraphWriter.DEFAULT_LABEL;
import static dev.langchain4j.community.rag.content.retriever.neo4j.KnowledgeGraphWriter.DEFAULT_REL_TYPE;
import static dev.langchain4j.community.rag.content.retriever.neo4j.KnowledgeGraphWriter.DEFAULT_TEXT_PROP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformer;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.Record;
import org.neo4j.driver.internal.util.Iterables;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
abstract class Neo4jKnowledgeGraphWriterBaseTest {

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

    public static final String ON = "on";
    public static final String KEY_CAT = "key2";
    public static final String VALUE_CAT = "value2";
    public static final String KEY_KEANU = "key33";
    public static final String VALUE_KEANU = "value3";
    public static String USERNAME = "neo4j";
    public static String ADMIN_PASSWORD = "adminPass";
    private static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "2025.01.0-enterprise");

    public static String CAT_ON_THE_TABLE = "Sylvester the cat is on the table";
    public static String KEANU_REEVES_ACTED = "Keanu Reeves acted in Matrix";
    public static String MATCH_AND_RETURN_NODE = "MATCH p=(n)-[]->() RETURN p ORDER BY n.%s";
    public static String MATCH_WITH_DOCUMENT_RETURN_NODE = "MATCH p=(:Document)-[]->(n)-[]->() RETURN p ORDER BY n.%s";
    public static String KEANU = "keanu";
    public static String MATRIX = "matrix";
    public static String ACTED = "acted";
    public static String SYLVESTER = "sylvester";
    public static String TABLE = "table";
    public static LLMGraphTransformer graphTransformer;
    public static List<GraphDocument> graphDocs;
    public static Neo4jGraph neo4jGraph;
    public static KnowledgeGraphWriter knowledgeGraphWriter;

    public static String CUSTOM_TEXT = "custom  `text";
    public static String CUSTOM_ID = "custom  ` id";
    public static final String SANITIZED_CUSTOM_ID =
            SchemaNames.sanitize(CUSTOM_ID).get();
    public static String CUSTOM_LABEL = "Label ` to \\ sanitize";

    @Container
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:" + NEO4J_VERSION))
            .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
            .withAdminPassword(ADMIN_PASSWORD)
            .withPlugins("apoc");

    @BeforeEach
    void beforeAll() {
        ChatModel model = getModel();

        graphTransformer = LLMGraphTransformer.builder()
                .model(model)
                .examples(EXAMPLES_PROMPT)
                .build();
        neo4jGraph = Neo4jGraph.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();
        knowledgeGraphWriter = KnowledgeGraphWriter.builder().graph(neo4jGraph).build();

        // given
        Document docKeanu = new DefaultDocument(KEANU_REEVES_ACTED, Metadata.from(KEY_KEANU, VALUE_KEANU));
        Document docCat = new DefaultDocument(CAT_ON_THE_TABLE, Metadata.from(KEY_CAT, VALUE_CAT));
        List<Document> documents = List.of(docCat, docKeanu);

        graphDocs = graphTransformer.transformAll(documents);
        assertThat(graphDocs.size()).isEqualTo(2);
    }

    abstract ChatModel getModel();

    @AfterEach
    void afterEach() {
        neo4jGraph.executeWrite("MATCH (n) DETACH DELETE n");
    }

    @AfterAll
    static void afterAll() {
        neo4jGraph.close();
    }

    @Test
    void testAddGraphDocuments() {

        addGraphDocumentsCommon();

        // retry to check that merge works correctly
        addGraphDocumentsCommon();
    }

    @Test
    void testAddGraphDocumentsWithCustomIdTextAndLabel() {

        knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                .graph(neo4jGraph)
                .textProperty(CUSTOM_TEXT)
                .idProperty(CUSTOM_ID)
                .label(CUSTOM_LABEL)
                .build();

        addGraphDocumentsWithCustomIdTextAndLabelCommon();

        // retry to check that merge works correctly
        addGraphDocumentsWithCustomIdTextAndLabelCommon();
    }

    @Test
    void testAddGraphDocumentsWithIncludeSource() {
        testWithIncludeSourceCommon(DEFAULT_REL_TYPE);

        // retry to check that merge works correctly
        testWithIncludeSourceCommon(DEFAULT_REL_TYPE);
    }

    @Test
    void testAddGraphDocumentsWithIncludeSourceAndCustomRelType() {
        final String customRelType = "CUSTOM_REL_TYPE";
        knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                .graph(neo4jGraph)
                .relType(customRelType)
                .build();

        testWithIncludeSourceCommon(customRelType);

        // retry to check that merge works correctly
        testWithIncludeSourceCommon(customRelType);
    }

    @Test
    void testAddGraphDocumentsWithIncludeSourceAndCustomIdTextAndLabel() {

        knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                .graph(Neo4jKnowledgeGraphWriterBaseTest.neo4jGraph)
                .textProperty(CUSTOM_TEXT)
                .idProperty(CUSTOM_ID)
                .label(CUSTOM_LABEL)
                .build();

        testWithIncludeSourceAndCustomIdTextAndLabelCommon(DEFAULT_REL_TYPE);

        // retry to check that merge works correctly
        testWithIncludeSourceAndCustomIdTextAndLabelCommon(DEFAULT_REL_TYPE);
    }

    @Test
    void testAddGraphDocumentsWithIncludeSourceAndCustomIdTextLabelRelType() {

        final String customRelType = "CUSTOM_REL_TYPE";
        knowledgeGraphWriter = KnowledgeGraphWriter.builder()
                .graph(neo4jGraph)
                .textProperty(CUSTOM_TEXT)
                .idProperty(CUSTOM_ID)
                .label(CUSTOM_LABEL)
                .relType(customRelType)
                .build();

        testWithIncludeSourceAndCustomIdTextAndLabelCommon(customRelType);

        // retry to check that merge works correctly
        testWithIncludeSourceAndCustomIdTextAndLabelCommon(customRelType);
    }

    private static void addGraphDocumentsCommon() {
        // when
        knowledgeGraphWriter.addGraphDocuments(graphDocs, false);

        // then
        List<Record> records = neo4jGraph.executeRead(MATCH_AND_RETURN_NODE.formatted(DEFAULT_ID_PROP));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Node start = path.start();
        assertNodeLabels(start, DEFAULT_LABEL);
        assertNodeProps(start, KEANU, DEFAULT_ID_PROP);
        Node end = path.end();
        assertNodeLabels(end, DEFAULT_LABEL);
        assertNodeProps(end, MATRIX, DEFAULT_ID_PROP);
        Relationship rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        start = path.start();
        assertNodeLabels(start, DEFAULT_LABEL);
        assertNodeProps(start, SYLVESTER, DEFAULT_ID_PROP);
        end = path.end();
        assertNodeLabels(end, DEFAULT_LABEL);
        assertNodeProps(end, TABLE, DEFAULT_ID_PROP);
        rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ON);
    }

    private static void addGraphDocumentsWithCustomIdTextAndLabelCommon() {
        knowledgeGraphWriter.addGraphDocuments(graphDocs, false);

        List<Record> records = neo4jGraph.executeRead(MATCH_AND_RETURN_NODE.formatted(SANITIZED_CUSTOM_ID));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Node start = path.start();
        assertNodeLabels(start, CUSTOM_LABEL);
        assertNodeProps(start, KEANU, CUSTOM_ID);
        Node end = path.end();
        assertNodeLabels(end, CUSTOM_LABEL);
        assertNodeProps(end, MATRIX, CUSTOM_ID);
        Relationship rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        start = path.start();
        assertNodeLabels(start, CUSTOM_LABEL);
        assertNodeProps(start, SYLVESTER, CUSTOM_ID);
        end = path.end();
        assertNodeLabels(end, CUSTOM_LABEL);
        assertNodeProps(end, TABLE, CUSTOM_ID);
        rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ON);
    }

    private static void testWithIncludeSourceCommon(String relType) {
        // when
        knowledgeGraphWriter.addGraphDocuments(graphDocs, true);

        // then
        List<Record> records = neo4jGraph.executeRead(MATCH_WITH_DOCUMENT_RETURN_NODE.formatted(DEFAULT_ID_PROP));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Iterator<Node> iterator = path.nodes().iterator();
        Node node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, KEANU_REEVES_ACTED, KEY_KEANU, VALUE_KEANU);

        node = iterator.next();
        assertNodeLabels(node, DEFAULT_LABEL);
        assertNodeProps(node, KEANU, DEFAULT_ID_PROP);

        node = iterator.next();
        assertNodeLabels(node, DEFAULT_LABEL);
        assertNodeProps(node, MATRIX, DEFAULT_ID_PROP);
        List<Relationship> rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(0).type()).containsIgnoringCase(relType);
        assertThat(rels.get(1).type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        iterator = path.nodes().iterator();
        node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, CAT_ON_THE_TABLE, KEY_CAT, VALUE_CAT);

        node = iterator.next();
        assertNodeLabels(node, DEFAULT_LABEL);
        assertNodeProps(node, SYLVESTER, DEFAULT_ID_PROP);

        node = iterator.next();
        assertNodeLabels(node, DEFAULT_LABEL);
        assertNodeProps(node, TABLE, DEFAULT_ID_PROP);
        rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(0).type()).containsIgnoringCase(relType);
        assertThat(rels.get(1).type()).containsIgnoringCase(ON);
    }

    private static void testWithIncludeSourceAndCustomIdTextAndLabelCommon(String relType) {
        knowledgeGraphWriter.addGraphDocuments(graphDocs, true);

        List<Record> records = neo4jGraph.executeRead(MATCH_WITH_DOCUMENT_RETURN_NODE.formatted(SANITIZED_CUSTOM_ID));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Iterator<Node> iterator = path.nodes().iterator();
        Node node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, CUSTOM_ID, CUSTOM_TEXT, KEANU_REEVES_ACTED, KEY_KEANU, VALUE_KEANU);

        node = iterator.next();
        assertNodeLabels(node, CUSTOM_LABEL);
        assertNodeProps(node, KEANU, CUSTOM_ID);

        node = iterator.next();
        assertNodeLabels(node, CUSTOM_LABEL);
        assertNodeProps(node, MATRIX, CUSTOM_ID);
        List<Relationship> rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(0).type()).containsIgnoringCase(relType);
        assertThat(rels.get(1).type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        iterator = path.nodes().iterator();
        node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, CUSTOM_ID, CUSTOM_TEXT, CAT_ON_THE_TABLE, KEY_CAT, VALUE_CAT);

        node = iterator.next();
        assertNodeLabels(node, CUSTOM_LABEL);
        assertNodeProps(node, SYLVESTER, CUSTOM_ID);

        node = iterator.next();
        assertNodeLabels(node, CUSTOM_LABEL);
        assertNodeProps(node, TABLE, CUSTOM_ID);
        rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(0).type()).containsIgnoringCase(relType);
        assertThat(rels.get(1).type()).containsIgnoringCase(ON);
    }

    private static void assertNodeLabels(Node start, String entityLabel) {
        Iterable<String> labels = start.labels();
        assertThat(labels).hasSize(2);
        assertThat(labels).contains(entityLabel);
    }

    private static void assertNodeProps(Node start, String propRegex, String idProp) {
        Map<String, Object> map = start.asMap();
        assertThat(map).hasSize(1);
        assertThat((String) map.get(idProp)).containsIgnoringCase(propRegex);
    }

    private static void assertionsDocument(
            Node start,
            String idProp,
            String textProp,
            String expectedText,
            String expectedMetaKey,
            String expectedMetaValue) {
        Map<String, Object> map = start.asMap();
        assertThat(map.size()).isEqualTo(3);
        assertThat(map).containsKey(idProp);
        Object text = map.get(textProp);
        assertThat(text).isEqualTo(expectedText);

        final Object actualMetaValue = map.get(expectedMetaKey);
        assertThat(actualMetaValue).isEqualTo(expectedMetaValue);
    }
}
