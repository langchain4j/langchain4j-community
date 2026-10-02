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
