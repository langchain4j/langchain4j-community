package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingStoreHybridSearchTestHelper.assertHybridSearch;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

class ArcadeDBEmbeddingStoreEmbeddedIT extends EmbeddingStoreWithFilteringIT {

    @TempDir
    Path tempDir;

    private ArcadeDBEmbeddingStore embeddingStore;

    private static final int DIMENSION = 384;
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            embeddingStore = ArcadeDBEmbeddingStore.embeddedBuilder()
                    .databasePath(tempDir.resolve("test-db-" + COUNTER.incrementAndGet())
                            .toString())
                    .dimension(DIMENSION)
                    .maxConnections(16)
                    .beamWidth(200)
                    .build();
        }
        return embeddingStore;
    }

    @AfterEach
    void teardown() {
        if (embeddingStore != null) {
            embeddingStore.close();
            embeddingStore = null;
        }
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @org.junit.jupiter.api.Test
    void should_perform_hybrid_search() {
        assertHybridSearch(embeddingStore(), embeddingModel());
    }

    @org.junit.jupiter.api.Test
    void should_search_hybrid_with_a_query_containing_a_line_break() {
        Embedding embedding = EMBEDDING_MODEL.embed("graph database").content();
        String id = embeddingStore().add(embedding, TextSegment.from("ArcadeDB is a graph database"));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .query("graph\ndatabase 'quoted' back\\slash")
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }

    @org.junit.jupiter.api.Test
    void should_search_hybrid_with_a_question_containing_lucene_syntax() {
        Embedding embedding = EMBEDDING_MODEL
                .embed("the three-fifths compromise in the constitution")
                .content();
        String id = embeddingStore()
                .add(
                        embedding,
                        TextSegment.from("The 3/5 compromise was stated in the constitution of the graph database"));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore()
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
        try (ArcadeDBEmbeddingStore raw = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("raw-db").toString())
                .dimension(DIMENSION)
                .rawFullTextQuery(true)
                .build()) {
            Embedding embedding = EMBEDDING_MODEL.embed("graph database").content();
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
    }

    @org.junit.jupiter.api.Test
    void should_replace_an_existing_id_and_allow_adding_it_again_after_removal() {
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("upsert-db").toString())
                .dimension(DIMENSION)
                .batchSize(3)
                .build()) {
            ArcadeDBUpsertTestHelper.assertUpsertAndReAdd(store, EMBEDDING_MODEL);
        }
    }

    @org.junit.jupiter.api.Test
    void should_keep_the_last_version_of_an_id_repeated_within_and_across_batches() {
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("repeated-db").toString())
                .dimension(DIMENSION)
                .batchSize(3)
                .build()) {
            ArcadeDBUpsertTestHelper.assertRepeatedIdsInOneAddAll(store, EMBEDDING_MODEL);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ArcadeDBHybridFusion.class)
    void should_search_hybrid_with_each_fusion(ArcadeDBHybridFusion fusion) {
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("fusion-" + fusion).toString())
                .dimension(DIMENSION)
                .hybridFusion(fusion)
                .hybridWeights(2, 1)
                .hybridRrfK(20)
                .build()) {
            assertHybridSearch(store, EMBEDDING_MODEL);
        }
    }

    @org.junit.jupiter.api.Test
    void should_stem_full_text_with_the_default_english_analyzer_but_not_with_the_standard_one() {
        assertThat(fullTextMatches(null, "The dogs were running", "runs")).isEqualTo(1);
        assertThat(fullTextMatches(
                        "org.apache.lucene.analysis.standard.StandardAnalyzer", "The dogs were running", "runs"))
                .isZero();
    }

    private long fullTextMatches(String analyzer, String text, String query) {
        String path = tempDir.resolve("analyzer-" + (analyzer == null ? "default" : "standard"))
                .toString();
        try (com.arcadedb.database.DatabaseFactory factory = new com.arcadedb.database.DatabaseFactory(path);
                com.arcadedb.database.Database database = factory.create()) {
            ArcadeDBEmbeddingStore.EmbeddedBuilder builder =
                    ArcadeDBEmbeddingStore.embeddedBuilder().database(database).dimension(DIMENSION);
            if (analyzer != null) {
                builder.fullTextAnalyzer(analyzer);
            }
            ArcadeDBEmbeddingStore store = builder.build();
            store.add(EMBEDDING_MODEL.embed(text).content(), TextSegment.from(text));
            try (com.arcadedb.query.sql.executor.ResultSet result = database.query(
                    "sql",
                    "SELECT count(*) AS n FROM EmbeddingDocument WHERE SEARCH_INDEX('EmbeddingDocument[text]', ?) = true",
                    query)) {
                return ((Number) result.next().getProperty("n")).longValue();
            }
        }
    }

    @org.junit.jupiter.api.Test
    void should_keep_the_existing_full_text_index_when_reopened_with_another_analyzer() {
        String path = tempDir.resolve("reopen-analyzer-db").toString();
        Embedding embedding = EMBEDDING_MODEL.embed("graph database").content();
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(path)
                .dimension(DIMENSION)
                .build()) {
            store.add(embedding, TextSegment.from("ArcadeDB is a graph database"));
        }
        try (ArcadeDBEmbeddingStore reopened = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(path)
                .dimension(DIMENSION)
                .fullTextAnalyzer("org.apache.lucene.analysis.standard.StandardAnalyzer")
                .fullTextBm25K1(2.0)
                .build()) {
            assertThat(reopened.search(EmbeddingSearchRequest.builder()
                                    .queryEmbedding(embedding)
                                    .query("databases")
                                    .maxResults(1)
                                    .build())
                            .matches())
                    .hasSize(1);
        }
    }

    @org.junit.jupiter.api.Test
    void should_never_lower_recall_with_a_wider_ef_search() {
        try (com.arcadedb.database.DatabaseFactory factory = new com.arcadedb.database.DatabaseFactory(
                        tempDir.resolve("ef-search-db").toString());
                com.arcadedb.database.Database database = factory.create()) {
            ArcadeDBEfSearchTestHelper.assertWiderBeamNeverLowersRecall(
                    beam -> ArcadeDBEmbeddingStore.embeddedBuilder()
                            .database(database)
                            .dimension(ArcadeDBEfSearchTestHelper.DIMENSION)
                            .efSearch(beam)
                            .build(),
                    110,
                    200,
                    800);
        }
    }

    @org.junit.jupiter.api.Test
    void should_reject_a_non_positive_ef_search() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ArcadeDBEmbeddingStore.embeddedBuilder()
                        .databasePath(tempDir.resolve("ef-zero").toString())
                        .dimension(DIMENSION)
                        .efSearch(-1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"COSINE", "EUCLIDEAN", "DOT_PRODUCT"})
    void should_rank_and_score_with_each_similarity_function(String similarity) {
        try (com.arcadedb.database.DatabaseFactory factory = new com.arcadedb.database.DatabaseFactory(
                        tempDir.resolve("similarity-" + similarity).toString());
                com.arcadedb.database.Database database = factory.create()) {
            ArcadeDBIndexOptionsTestHelper.assertSimilarityScores(
                    ArcadeDBEmbeddingStore.embeddedBuilder()
                            .database(database)
                            .dimension(ArcadeDBIndexOptionsTestHelper.SIMILARITY_DIMENSION)
                            .similarityFunction(similarity)
                            .build(),
                    similarity);
            assertThat(vectorIndexMetadata(database).similarityFunction.name()).isEqualTo(similarity);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ArcadeDBVectorQuantization.class)
    void should_search_with_each_quantization(ArcadeDBVectorQuantization quantization) {
        try (com.arcadedb.database.DatabaseFactory factory = new com.arcadedb.database.DatabaseFactory(
                        tempDir.resolve("quantization-" + quantization).toString());
                com.arcadedb.database.Database database = factory.create()) {
            ArcadeDBIndexOptionsTestHelper.assertFindsStoredVector(ArcadeDBEmbeddingStore.embeddedBuilder()
                    .database(database)
                    .dimension(ArcadeDBIndexOptionsTestHelper.QUANTIZATION_DIMENSION)
                    .quantization(quantization)
                    .build());
            assertThat(vectorIndexMetadata(database).quantizationType.name()).isEqualTo(quantization.name());
        }
    }

    @org.junit.jupiter.api.Test
    void should_keep_similarity_and_quantization_when_removing_all() {
        try (com.arcadedb.database.DatabaseFactory factory = new com.arcadedb.database.DatabaseFactory(
                        tempDir.resolve("remove-all-options").toString());
                com.arcadedb.database.Database database = factory.create()) {
            ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                    .database(database)
                    .dimension(ArcadeDBIndexOptionsTestHelper.SIMILARITY_DIMENSION)
                    .similarityFunction("EUCLIDEAN")
                    .quantization(ArcadeDBVectorQuantization.INT8)
                    .build();
            store.add(new Embedding(new float[] {1, 0, 0, 0}));
            store.removeAll();
            assertThat(vectorIndexMetadata(database).similarityFunction.name()).isEqualTo("EUCLIDEAN");
            assertThat(vectorIndexMetadata(database).quantizationType.name()).isEqualTo("INT8");
        }
    }

    private static com.arcadedb.schema.LSMVectorIndexMetadata vectorIndexMetadata(
            com.arcadedb.database.Database database) {
        com.arcadedb.index.TypeIndex index =
                (com.arcadedb.index.TypeIndex) database.getSchema().getIndexByName("EmbeddingDocument[embedding]");
        return ((com.arcadedb.index.vector.LSMVectorIndex) index.getIndexesOnBuckets()[0]).getMetadata();
    }

    @org.junit.jupiter.api.Test
    void should_return_every_record_of_identical_vectors() {
        // Identical vectors can leave parts of the graph unreachable; the search must still return all of them.
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("identical-vectors").toString())
                .dimension(DIMENSION)
                .build()) {
            Embedding embedding = EMBEDDING_MODEL.embed("the same text").content();
            List<Embedding> embeddings = java.util.Collections.nCopies(60, embedding);
            List<String> ids = store.addAll(embeddings);

            assertThat(store.search(EmbeddingSearchRequest.builder()
                                    .queryEmbedding(embedding)
                                    .maxResults(60)
                                    .build())
                            .matches())
                    .extracting(EmbeddingMatch::embeddingId)
                    .containsExactlyInAnyOrderElementsOf(ids);
        }
    }

    @org.junit.jupiter.api.Test
    void should_not_return_removed_records_when_pre_filtering() {
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("pre-filter-removed").toString())
                .dimension(DIMENSION)
                .filterStrategy(ArcadeDBFilterStrategy.PRE_FILTER)
                .build()) {
            Embedding embedding = EMBEDDING_MODEL.embed("graph database").content();
            List<String> ids = store.addAll(
                    List.of(embedding, embedding, embedding),
                    List.of(
                            TextSegment.from("graph database one", new Metadata(Map.of("bucket", 0))),
                            TextSegment.from("graph database two", new Metadata(Map.of("bucket", 0))),
                            TextSegment.from("graph database three", new Metadata(Map.of("bucket", 1)))));
            store.remove(ids.get(0));

            for (String text : new String[] {null, "database"}) {
                assertThat(store.search(EmbeddingSearchRequest.builder()
                                        .queryEmbedding(embedding)
                                        .query(text)
                                        .filter(
                                                dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                                                        .metadataKey("bucket")
                                                        .isLessThan(1))
                                        .maxResults(10)
                                        .build())
                                .matches())
                        .as(text == null ? "dense" : "hybrid")
                        .extracting(EmbeddingMatch::embeddingId)
                        .containsExactly(ids.get(1));
            }
        }
    }

    @org.junit.jupiter.api.Test
    void should_return_all_matches_of_a_selective_filter_unless_post_filtering() {
        List<ArcadeDBEmbeddingStore> stores = new java.util.ArrayList<>();
        try {
            ArcadeDBSelectiveFilterTestHelper.assertStrategies(strategy -> {
                ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                        .databasePath(tempDir.resolve("selective-" + strategy).toString())
                        .dimension(ArcadeDBSelectiveFilterTestHelper.DIMENSION)
                        .filterStrategy(strategy)
                        .build();
                stores.add(store);
                return store;
            });
        } finally {
            stores.forEach(ArcadeDBEmbeddingStore::close);
        }
    }

    @org.junit.jupiter.api.Test
    void should_add_all_in_several_batches() {
        try (ArcadeDBEmbeddingStore batched = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(tempDir.resolve("batched-db").toString())
                .dimension(DIMENSION)
                .batchSize(3)
                .build()) {
            List<String> ids = new ArrayList<>();
            List<Embedding> embeddings = new ArrayList<>();
            List<TextSegment> segments = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                ids.add("id-" + i);
                embeddings.add(EMBEDDING_MODEL.embed("batched text " + i).content());
                segments.add(TextSegment.from("batched text " + i, new Metadata(Map.of("key" + (i % 4), i))));
            }

            batched.addAll(ids, embeddings, segments);

            for (int i = 0; i < 10; i++) {
                int n = i;
                List<EmbeddingMatch<TextSegment>> matches = batched.search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddings.get(i))
                                .maxResults(1)
                                .build())
                        .matches();
                assertThat(matches).singleElement().satisfies(match -> {
                    assertThat(match.embeddingId()).isEqualTo(ids.get(n));
                    assertThat(match.embedded().text()).isEqualTo("batched text " + n);
                    assertThat(match.embedded().metadata().getInteger("key" + (n % 4)))
                            .isEqualTo(n);
                });
            }
        }
    }

    @org.junit.jupiter.api.Test
    void should_reopen_existing_store_with_different_index_settings() {
        String path = tempDir.resolve("reopened-db").toString();
        Embedding embedding = EMBEDDING_MODEL.embed("reopened store").content();
        String id;
        try (ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(path)
                .dimension(DIMENSION)
                .maxConnections(16)
                .beamWidth(200)
                .build()) {
            id = store.add(embedding);
        }

        // The existing index is kept; the different settings do not fail the builder.
        try (ArcadeDBEmbeddingStore reopened = ArcadeDBEmbeddingStore.embeddedBuilder()
                .databasePath(path)
                .dimension(DIMENSION)
                .maxConnections(32)
                .beamWidth(100)
                .build()) {
            List<EmbeddingMatch<TextSegment>> matches = reopened.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding)
                            .maxResults(1)
                            .build())
                    .matches();
            assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
        }
    }
}
