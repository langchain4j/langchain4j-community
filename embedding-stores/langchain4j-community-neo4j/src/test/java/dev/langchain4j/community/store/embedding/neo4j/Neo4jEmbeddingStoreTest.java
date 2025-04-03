package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore.COLUMNS_NOT_ALLOWED_ERR;
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
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;

public class Neo4jEmbeddingStoreTest extends Neo4jEmbeddingStoreBaseTest {

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
    void should_retrieve_custom_metadata_with_match_invalid() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .retrievalQuery("RETURN {foo: 'bar'} AS invalidColumn")
                .build();

        String text = randomUUID();
        TextSegment segment = TextSegment.from(text, Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        try {
            final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(10)
                    .build();
            embeddingStore.search(request).matches();
            fail("Should fail due to: " + COLUMNS_NOT_ALLOWED_ERR);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains(COLUMNS_NOT_ALLOWED_ERR);
        }
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
    void should_throws_error_if_full_text_retrieval_is_invalid() {
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .fullTextIndexName("full_text_with_invalid_retrieval")
                .fullTextQuery("Matrix")
                .autoCreateFullText(true)
                .fullTextRetrievalQuery("RETURN properties(invalid) AS metadata")
                .label(LABEL_TO_SANITIZE)
                .build();

        List<Embedding> embeddings =
                embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
        embeddingStore.addAll(embeddings);

        final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        try {
            embeddingStore.search(embeddingSearchRequest).matches();
            fail("should fail due to not existent index");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Variable `invalid` not defined");
        }
    }

    @Test
    void row_batches_20000_elements_and_full_text() {
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label("labelBatch")
                .indexName("movie_vector")
                .fullTextIndexName("fullTextIndexNameBatch")
                .fullTextQuery("fullTextSearchBatch")
                .build();
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(20000, embeddingStore);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(10000);
    }

    @Test
    void should_throws_error_if_fulltext_doesnt_exist() {
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .fullTextIndexName("movie_text_non_existent")
                .fullTextQuery("Matrix")
                .label(LABEL_TO_SANITIZE)
                .build();

        List<Embedding> embeddings =
                embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
        embeddingStore.addAll(embeddings);

        final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        try {
            embeddingStore.search(embeddingSearchRequest).matches();
            fail("should fail due to not existent index");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("There is no such fulltext schema index");
        }
    }

    @Test
    void should_get_embeddings_if_autocreate_full_text_is_true() {
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .fullTextIndexName("movie_text")
                .fullTextQuery("Matrix")
                .autoCreateFullText(true)
                .label(LABEL_TO_SANITIZE)
                .build();

        List<Embedding> embeddings =
                embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
        embeddingStore.addAll(embeddings);

        final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1)
                .build();

        final List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(embeddingSearchRequest).matches();
        assertThat(matches).hasSize(1);
    }

    @Test
    void should_add_embedding_and_fulltext_with_id() {
        final String fullTextIndexName = "movie_text";
        final String label = "Movie";
        final String fullTextSearch = "Matrix";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("movie_vector_idx")
                .fullTextIndexName(fullTextIndexName)
                .fullTextQuery(fullTextSearch)
                .build();

        final List<String> texts = List.of(
                "The Matrix: Welcome to the Real World",
                "The Matrix Reloaded: Free your mind",
                "The Matrix Revolutions: Everything that has a beginning has an end",
                "The Devil's Advocate: Evil has its winning ways",
                "A Few Good Men: In the heart of the nation's capital, in a courthouse of the U.S. government, one man will stop at nothing to keep his honor, and one will stop at nothing to find the truth.",
                "Top Gun: I feel the need, the need for speed.",
                "Jerry Maguire: The rest of his life begins now.",
                "Stand By Me: For some, it's the last real taste of innocence, and the first real taste of life. But for everyone, it's the time that memories are made of.",
                "As Good as It Gets: A comedy from the heart that goes for the throat.");

        final List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        final Embedding queryEmbedding = embeddingModel.embed(fullTextSearch).content();

        session.executeWrite(tx -> {
            final String query = String.format(
                    "CREATE FULLTEXT INDEX %s IF NOT EXISTS FOR (e:%s) ON EACH [e.%s]",
                    fullTextIndexName, label, DEFAULT_ID_PROP);
            tx.run(query).consume();
            return null;
        });

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        final List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(embeddingSearchRequest).matches();
        assertThat(matches).hasSize(3);
        matches.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains(fullTextSearch);
        });

        Neo4jEmbeddingStore embeddingStoreWithoutFullText = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("movie_vector_no_fulltext")
                .build();

        embeddingStoreWithoutFullText.addAll(embeddings, segments);
        final List<EmbeddingMatch<TextSegment>> matchesWithoutFullText =
                embeddingStore.search(embeddingSearchRequest).matches();
        assertThat(matchesWithoutFullText).hasSize(3);
        matchesWithoutFullText.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains(fullTextSearch);
        });
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
        return getListRowsBatched(numElements, (Neo4jEmbeddingStore) embeddingStore);
    }

    private List<List<Map<String, Object>>> getListRowsBatched(int numElements, Neo4jEmbeddingStore embeddingStore) {
        List<TextSegment> embedded = IntStream.range(0, numElements)
                .mapToObj(i -> TextSegment.from("text-" + i))
                .toList();
        List<String> ids =
                IntStream.range(0, numElements).mapToObj(i -> "id-" + i).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(embedded).content();

        return Neo4jEmbeddingUtils.getRowsBatched(embeddingStore, ids, embeddings, embedded)
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
