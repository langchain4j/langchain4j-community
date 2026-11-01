package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingStoreHybridSearchTestHelper.assertHybridSearch;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

class ArcadeDBEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static GenericContainer<?> arcadedb;
    private static ArcadeDBEmbeddingStore embeddingStore;
    private static String host;
    private static int port;

    @BeforeAll
    static void startServer() {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            arcadedb = ArcadeDBTestServer.newContainer();
            arcadedb.start();
            host = arcadedb.getHost();
            port = arcadedb.getMappedPort(ArcadeDBTestServer.HTTP_PORT);
        } else {
            host = "localhost";
            port = ArcadeDBTestServer.HTTP_PORT;
        }

        embeddingStore = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .maxConnections(128)
                .beamWidth(500)
                .createDatabase(true)
                .build();
    }

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

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
        embeddingStore().removeAll();
    }

    @Override
    protected boolean testFloatExactly() {
        return false;
    }

    @Override
    protected boolean testDoubleExactly() {
        // ArcadeDB cannot store Double.MIN_VALUE (4.9E-324) precisely — it underflows to 0.0.
        return false;
    }

    @Override
    protected double doublePercentage() {
        // Double.MIN_VALUE underflows to 0.0, which is 100% off. Use 200 to give clear margin
        // above the boundary where subnormal floating-point arithmetic can cause a false failure.
        return 200;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @org.junit.jupiter.api.Test
    void should_perform_hybrid_search() {
        assertHybridSearch(embeddingStore(), embeddingModel);
    }

    @org.junit.jupiter.api.Test
    void should_return_the_nearest_neighbours_rather_than_the_first_rows_in_scan_order() {
        clearStore();
        // The nearest neighbours of the query are among the last records inserted, i.e. last in scan order.
        Random random = new Random(42);
        List<float[]> vectors = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            float[] vector = new float[384];
            for (int d = 0; d < vector.length; d++) {
                vector[d] = (float) random.nextGaussian();
            }
            vectors.add(vector);
            ids.add(embeddingStore.add(
                    Embedding.from(vector),
                    TextSegment.from("record " + i, Metadata.from("group", i % 2 == 0 ? "even" : "odd"))));
        }
        float[] query = vectors.get(37).clone();
        query[0] += 0.5f;

        List<EmbeddingMatch<TextSegment>> nearest = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(query))
                        .maxResults(3)
                        .build())
                .matches();
        List<EmbeddingMatch<TextSegment>> nearestEven = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(query))
                        .maxResults(3)
                        .filter(MetadataFilterBuilder.metadataKey("group").isEqualTo("even"))
                        .build())
                .matches();

        assertThat(nearest)
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactlyElementsOf(bruteForce(query, vectors, ids, i -> true));
        assertThat(nearestEven)
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactlyElementsOf(bruteForce(query, vectors, ids, i -> i % 2 == 0));
        assertThat(nearest.get(0).embeddingId()).isEqualTo(ids.get(37));
    }

    private static List<String> bruteForce(
            float[] query, List<float[]> vectors, List<String> ids, java.util.function.IntPredicate include) {
        return IntStream.range(0, vectors.size())
                .filter(include)
                .boxed()
                .sorted(Comparator.comparingDouble((Integer i) ->
                        -CosineSimilarity.between(Embedding.from(query), Embedding.from(vectors.get(i)))))
                .limit(3)
                .map(ids::get)
                .toList();
    }

    static final String AWKWARD = "Title\nline two\r\nwith 'single' and \"double\" quotes,\ta tab and a back\\slash";

    @org.junit.jupiter.api.Test
    void should_store_and_find_texts_and_metadata_with_line_breaks_quotes_and_backslashes() {
        clearStore();
        Embedding embedding = embeddingModel.embed("awkward text").content();
        String id = embeddingStore.add(embedding, TextSegment.from(AWKWARD, Metadata.from("note", AWKWARD)));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.embeddingId()).isEqualTo(id);
            assertThat(match.embedded().text()).isEqualTo(AWKWARD);
            assertThat(match.embedded().metadata().getString("note")).isEqualTo(AWKWARD);
        });
    }

    @org.junit.jupiter.api.Test
    void should_search_hybrid_with_a_query_containing_a_line_break() {
        clearStore();
        Embedding embedding = embeddingModel.embed("graph database").content();
        String id = embeddingStore.add(embedding, TextSegment.from("ArcadeDB is a graph database"));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .query("graph\ndatabase")
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }

    @org.junit.jupiter.api.Test
    void should_remove_by_ids_and_by_filter_with_line_breaks_quotes_and_backslashes() {
        clearStore();
        Embedding embedding = embeddingModel.embed("to be removed").content();
        String awkwardId = "id with 'quote'\nand back\\slash";
        embeddingStore.addAll(List.of(awkwardId), List.of(embedding), List.of(TextSegment.from("by id")));
        embeddingStore.add(embedding, TextSegment.from("by filter", Metadata.from("note", AWKWARD)));
        String kept = embeddingStore.add(embedding, TextSegment.from("kept", Metadata.from("note", "plain")));

        embeddingStore.removeAll(List.of(awkwardId));
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("note").isEqualTo(AWKWARD));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(10)
                        .build())
                .matches();
        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(kept);
    }

    @org.junit.jupiter.api.Test
    void should_search_hybrid_with_a_question_containing_lucene_syntax() {
        clearStore();
        Embedding embedding = embeddingModel
                .embed("the three-fifths compromise in the constitution")
                .content();
        String id = embeddingStore.add(
                embedding, TextSegment.from("The 3/5 compromise was stated in the constitution of the graph database"));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .query("where was the \"3/5 compromise\" (graph: database) stated AND -why? [ok] ~fuzzy* ^2")
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }

    @org.junit.jupiter.api.Test
    void should_fall_back_to_dense_search_when_a_raw_full_text_query_is_invalid() {
        clearStore();
        ArcadeDBEmbeddingStore raw = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .rawFullTextQuery(true)
                .build();
        Embedding embedding = embeddingModel.embed("graph database").content();
        String id = raw.add(embedding, TextSegment.from("ArcadeDB is a graph database"));

        List<EmbeddingMatch<TextSegment>> valid = raw.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .query("graph AND database")
                        .maxResults(1)
                        .build())
                .matches();
        List<EmbeddingMatch<TextSegment>> invalid = raw.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .query("graph AND (database")
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(valid).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
        assertThat(invalid).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }

    private ArcadeDBEmbeddingStore storeWithBatchSize3() {
        return ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .batchSize(3)
                .build();
    }

    @org.junit.jupiter.api.Test
    void should_replace_an_existing_id_and_allow_adding_it_again_after_removal() {
        clearStore();
        ArcadeDBUpsertTestHelper.assertUpsertAndReAdd(storeWithBatchSize3(), embeddingModel);
    }

    @org.junit.jupiter.api.Test
    void should_keep_the_last_version_of_an_id_repeated_within_and_across_batches() {
        clearStore();
        ArcadeDBUpsertTestHelper.assertRepeatedIdsInOneAddAll(storeWithBatchSize3(), embeddingModel);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ArcadeDBHybridFusion.class)
    void should_search_hybrid_with_each_fusion(ArcadeDBHybridFusion fusion) {
        clearStore();
        ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .hybridFusion(fusion)
                .hybridWeights(2, 1)
                .hybridRrfK(20)
                .build();
        assertHybridSearch(store, embeddingModel);
    }

    @org.junit.jupiter.api.Test
    void should_create_a_full_text_index_with_custom_analyzer_and_bm25_settings() {
        ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("CustomFullTextDocument")
                .dimension(384)
                .fullTextAnalyzer("org.apache.lucene.analysis.standard.StandardAnalyzer")
                .fullTextBm25K1(1.5)
                .fullTextBm25B(0.5)
                .build();
        assertHybridSearch(store, embeddingModel);
    }

    @org.junit.jupiter.api.Test
    void should_never_lower_recall_with_a_wider_ef_search() {
        ArcadeDBEfSearchTestHelper.assertWiderBeamNeverLowersRecall(
                beam -> ArcadeDBEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .databaseName("test_embeddings")
                        .username("root")
                        .password(ArcadeDBTestServer.ROOT_PASSWORD)
                        .typeName("EfSearchDocument")
                        .dimension(ArcadeDBEfSearchTestHelper.DIMENSION)
                        .efSearch(beam)
                        .build(),
                10,
                40,
                200);
    }

    @org.junit.jupiter.api.Test
    void should_reject_a_non_positive_ef_search() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ArcadeDBEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .databaseName("test_embeddings")
                        .dimension(384)
                        .efSearch(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"COSINE", "EUCLIDEAN", "DOT_PRODUCT"})
    void should_rank_and_score_with_each_similarity_function(String similarity) {
        ArcadeDBIndexOptionsTestHelper.assertSimilarityScores(
                ArcadeDBEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .databaseName("test_embeddings")
                        .username("root")
                        .password(ArcadeDBTestServer.ROOT_PASSWORD)
                        .typeName("Similarity" + similarity)
                        .dimension(ArcadeDBIndexOptionsTestHelper.SIMILARITY_DIMENSION)
                        .similarityFunction(similarity)
                        .build(),
                similarity);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ArcadeDBVectorQuantization.class)
    void should_search_with_each_quantization(ArcadeDBVectorQuantization quantization) {
        ArcadeDBIndexOptionsTestHelper.assertFindsStoredVector(ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("Quantization" + quantization)
                .dimension(ArcadeDBIndexOptionsTestHelper.QUANTIZATION_DIMENSION)
                .quantization(quantization)
                .build());
    }

    @org.junit.jupiter.api.Test
    void should_reject_an_unsupported_similarity_function() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ArcadeDBEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .databaseName("test_embeddings")
                        .dimension(384)
                        .similarityFunction("SQUARED_EUCLIDEAN")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQUARED_EUCLIDEAN");
    }

    @org.junit.jupiter.api.Test
    void should_remove_by_a_float_metadata_filter() {
        clearStore();
        Embedding embedding = embeddingModel.embed("float metadata").content();
        embeddingStore.add(embedding, TextSegment.from("one point one", new Metadata(Map.of("key", 1.1f))));
        embeddingStore.add(embedding, TextSegment.from("two point two", new Metadata(Map.of("key", 2.2f))));

        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("key").isEqualTo(1.1f));

        assertThat(embeddingStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(10)
                                .build())
                        .matches())
                .extracting(m -> m.embedded().text())
                .containsExactly("two point two");
    }

    @org.junit.jupiter.api.Test
    void should_return_all_matches_of_a_selective_filter_unless_post_filtering() {
        ArcadeDBSelectiveFilterTestHelper.assertStrategies(strategy -> ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("Selective" + strategy)
                .dimension(ArcadeDBSelectiveFilterTestHelper.DIMENSION)
                .filterStrategy(strategy)
                .build());
    }

    @org.junit.jupiter.api.Test
    void should_add_all_in_several_batches() {
        clearStore();
        ArcadeDBEmbeddingStore batched = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .batchSize(3)
                .build();
        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add("batched-" + i);
            embeddings.add(embeddingModel.embed("batched text " + i).content());
            // Rows in one batch carry different metadata keys.
            segments.add(TextSegment.from("batched text " + i, new Metadata(Map.of("key" + (i % 4), i))));
        }

        batched.addAll(ids, embeddings, segments);

        for (int i = 0; i < 10; i++) {
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embeddings.get(i))
                            .maxResults(1)
                            .build())
                    .matches();
            int n = i;
            assertThat(matches).singleElement().satisfies(match -> {
                assertThat(match.embeddingId()).isEqualTo(ids.get(n));
                assertThat(match.embedded().text()).isEqualTo("batched text " + n);
                assertThat(match.embedded().metadata().getInteger("key" + (n % 4)))
                        .isEqualTo(n);
            });
        }
    }

    @org.junit.jupiter.api.Test
    void should_reopen_existing_store_with_different_index_settings() {
        // ArcadeDB 26.8.1+ rejects CREATE INDEX IF NOT EXISTS when the existing index has another configuration.
        // Like embedded mode, the store keeps the existing index.
        ArcadeDBEmbeddingStore reopened = ArcadeDBEmbeddingStore.builder()
                .host(host)
                .port(port)
                .databaseName("test_embeddings")
                .username("root")
                .password(ArcadeDBTestServer.ROOT_PASSWORD)
                .typeName("EmbeddingDocument")
                .dimension(384)
                .maxConnections(16)
                .beamWidth(100)
                .build();

        Embedding embedding = embeddingModel.embed("reopened store").content();
        String id = reopened.add(embedding);

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .build())
                .matches();
        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }
}
