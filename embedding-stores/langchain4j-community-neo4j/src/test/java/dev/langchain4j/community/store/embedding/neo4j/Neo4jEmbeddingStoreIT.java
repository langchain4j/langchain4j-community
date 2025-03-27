package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Neo4jEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String USERNAME = "neo4j";
    private static final String ADMIN_PASSWORD = "adminPass";
    private static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";
    private static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");

    @Container
    static Neo4jContainer<?> neo4jContainer =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:" + NEO4J_VERSION)).withAdminPassword(ADMIN_PASSWORD);

    private static final String METADATA_KEY = "test-key";

    private EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static Session session;

    @BeforeAll
    static void beforeAll() {
        neo4jContainer.start();
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
        session = driver.session();
    }

    @AfterAll
    static void afterAll() {
        session.close();
        neo4jContainer.stop();
    }

    @AfterEach
    void afterEach() {
        session.run("MATCH (n) DETACH DELETE n");
        String indexName = ((Neo4jEmbeddingStore) embeddingStore()).getIndexName();
        session.run("DROP INDEX " + SchemaNames.sanitize(indexName).get());
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_TO_SANITIZE)
                .build();
    }

    @Test
    void should_add_embedding_and_check_entity_creation() {
        Embedding embedding = embeddingModel.embed("embedText").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        final List<EmbeddingMatch<TextSegment>> relevant =
                embeddingStore.search(request).matches();
        assertThat(relevant).hasSize(1);
        EmbeddingMatch<TextSegment> match = relevant.get(0);

        checkEntitiesCreated(relevant.size(), iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_add_embedding_with_segment_with_custom_metadata_prefix() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .build();

        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey, DEFAULT_ID_PROP, labelName);
    }

    @Test
    void should_retrieve_custom_metadata_with_match() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .retrievalQuery(
                        "RETURN {foo: 'bar'} AS metadata, node.text AS text, node.embedding AS embedding, node.id AS id, score")
                .build();

        String text = randomUUID();
        TextSegment segment = TextSegment.from(text, Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        final List<EmbeddingMatch<TextSegment>> relevant =
                embeddingStore.search(request).matches();
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);

        TextSegment customMeta = TextSegment.from(text, Metadata.from("foo", "bar"));
        assertThat(match.embedded()).isEqualTo(customMeta);

        checkEntitiesCreated(relevant.size(), labelName, iterator -> {
            List<String> otherProps = Arrays.asList(DEFAULT_TEXT_PROP, metadataPrefix + METADATA_KEY);
            checkDefaultProps(embedding, DEFAULT_ID_PROP, match, iterator.next(), otherProps);
        });
    }

    @Test
    void should_add_embedding_with_segment_with_metadata_and_custom_id_prop() {
        String metadataPrefix = "metadata.";
        String customIdProp = "customId ` & Prop ` To Sanitize";

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label("CustomLabelName")
                .indexName("customIdxName")
                .idProperty(customIdProp)
                .build();

        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey, customIdProp, "CustomLabelName");
    }

    @Test
    void should_add_multiple_embeddings_and_create_entities() {
        Embedding firstEmbedding = embeddingModel.embed("firstEmbedText").content();
        Embedding secondEmbedding = embeddingModel.embed("secondEmbedText").content();

        List<String> ids = embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);

        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
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

    private void checkSegmentWithMetadata(String metadataKey, String idProp, String labelName) {
        TextSegment segment = TextSegment.from(randomUUID(), Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
        final List<EmbeddingMatch<TextSegment>> relevant =
                embeddingStore.search(request).matches();
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);

        checkEntitiesCreated(relevant.size(), labelName, iterator -> {
            List<String> otherProps = Arrays.asList(DEFAULT_TEXT_PROP, metadataKey);
            checkDefaultProps(embedding, idProp, match, iterator.next(), otherProps);
        });
    }

    private void checkEntitiesCreated(int expectedSize, Consumer<Iterator<Node>> nodeConsumer) {
        checkEntitiesCreated(expectedSize, LABEL_TO_SANITIZE, nodeConsumer);
    }

    private void checkEntitiesCreated(int expectedSize, String labelName, Consumer<Iterator<Node>> nodeConsumer) {
        String query = String.format(
                "MATCH (n:%s) RETURN n ORDER BY n.%s",
                SchemaNames.sanitize(labelName).get(), DEFAULT_TEXT_PROP);

        List<Node> n = session.run(query).list(i -> i.get("n").asNode());

        assertThat(n).hasSize(expectedSize);

        Iterator<Node> iterator = n.iterator();
        nodeConsumer.accept(iterator);

        assertThat(iterator).isExhausted();
    }

    private void checkDefaultProps(Embedding embedding, EmbeddingMatch<TextSegment> match, Node node) {
        checkDefaultProps(embedding, DEFAULT_ID_PROP, match, node, Collections.emptyList());
    }

    private void checkDefaultProps(
            Embedding embedding, String idProp, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkPropKeys(node, idProp, otherProps);

        assertThat(node.get(idProp).asString()).isEqualTo(match.embeddingId());

        List<Float> floats = node.get(DEFAULT_EMBEDDING_PROP).asList(Value::asFloat);
        assertThat(floats).isEqualTo(embedding.vectorAsList());
    }

    private void checkPropKeys(Node node, String idProp, List<String> otherProps) {
        List<String> strings = new ArrayList<>();
        // default props
        strings.add(idProp);
        strings.add(DEFAULT_EMBEDDING_PROP);
        // other props
        strings.addAll(otherProps);

        assertThat(node.keys()).containsExactlyInAnyOrderElementsOf(strings);
    }
}
