package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class Neo4jEmbeddingStoreBaseTest extends EmbeddingStoreIT {

    protected static final String USERNAME = "neo4j";
    protected static final String ADMIN_PASSWORD = "adminPass";
    protected static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";
    protected static Neo4jContainer<?> neo4jContainer;

    protected static final String METADATA_KEY = "test-key";

    protected Neo4jEmbeddingStore embeddingStore;

    protected final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    protected static Session session;

    @BeforeAll
    static void beforeAll(TestInfo testInfo) {
        // If test class name contains "2026" the default neo4j version is ""2026.x.y-enterprise"
        // otherwise the 5.26.y-enterprise
        String defaultVersion = testInfo.getDisplayName().contains("2026") ? "2026.01.3-enterprise" : "5.26-enterprise";
        String version = System.getProperty("neo4jVersion", defaultVersion);

        neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:" + version))
                .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                .withAdminPassword(ADMIN_PASSWORD);

        neo4jContainer.start();
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
        session = driver.session();
    }

    @AfterAll
    static void afterAll() {
        session.close();
        neo4jContainer.stop();
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
        session.executeWriteWithoutResult(
                tx -> tx.run("MATCH (n) DETACH DELETE n").consume());
        session.run("CALL db.awaitIndexes()");

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_TO_SANITIZE)
                .build();
    }

    /*
    Common methods
    */
    List<List<Map<String, Object>>> getListRowsBatched(int numElements) {
        return getListRowsBatched(numElements, embeddingStore);
    }

    List<List<Map<String, Object>>> getListRowsBatched(int numElements, Neo4jEmbeddingStore embeddingStore) {
        List<TextSegment> embedded = IntStream.range(0, numElements)
                .mapToObj(i -> TextSegment.from("text-" + i))
                .toList();
        List<String> ids =
                IntStream.range(0, numElements).mapToObj(i -> "id-" + i).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(embedded).content();

        return Neo4jEmbeddingUtils.getRowsBatched(embeddingStore, ids, embeddings, embedded)
                .toList();
    }

    void checkSegmentWithMetadata(String metadataKey, String idProp, String labelName) {
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

    void checkEntitiesCreated(int expectedSize, Consumer<Iterator<Node>> nodeConsumer) {
        checkEntitiesCreated(expectedSize, LABEL_TO_SANITIZE, nodeConsumer);
    }

    void checkEntitiesCreated(int expectedSize, String labelName, Consumer<Iterator<Node>> nodeConsumer) {
        String query = String.format(
                "MATCH (n:%s) RETURN n ORDER BY n.%s",
                SchemaNames.sanitize(labelName).get(), DEFAULT_TEXT_PROP);

        List<Node> n = session.run(query).list(i -> i.get("n").asNode());

        assertThat(n).hasSize(expectedSize);

        Iterator<Node> iterator = n.iterator();
        nodeConsumer.accept(iterator);

        assertThat(iterator).isExhausted();
    }

    void checkDefaultProps(Embedding embedding, EmbeddingMatch<TextSegment> match, Node node) {
        checkDefaultProps(embedding, DEFAULT_ID_PROP, match, node, Collections.emptyList());
    }

    void checkDefaultProps(
            Embedding embedding, String idProp, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkPropKeys(node, idProp, otherProps);

        assertThat(node.get(idProp).asString()).isEqualTo(match.embeddingId());

        List<Float> floats = node.get(DEFAULT_EMBEDDING_PROP).asList(Value::asFloat);
        assertThat(floats).isEqualTo(embedding.vectorAsList());
    }

    void checkPropKeys(Node node, String idProp, List<String> otherProps) {
        List<String> strings = new ArrayList<>();
        // default props
        strings.add(idProp);
        strings.add(DEFAULT_EMBEDDING_PROP);
        // other props
        strings.addAll(otherProps);

        assertThat(node.keys()).containsExactlyInAnyOrderElementsOf(strings);
    }
}
