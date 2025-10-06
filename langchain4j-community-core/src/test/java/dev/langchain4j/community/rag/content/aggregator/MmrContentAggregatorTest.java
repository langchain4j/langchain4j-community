package dev.langchain4j.community.rag.content.aggregator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.rag.content.aggregator.strategy.EmbeddingStrategy;
import dev.langchain4j.community.rag.content.aggregator.strategy.GenerateEmbeddings;
import dev.langchain4j.community.rag.content.aggregator.strategy.HybridEmbeddings;
import dev.langchain4j.community.rag.content.aggregator.strategy.UseExistingEmbeddings;
import dev.langchain4j.community.rag.content.util.EmbeddingMetadataUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class MmrContentAggregatorTest {

    @ParameterizedTest
    @MethodSource
    void should_apply_mmr_with_auto_strategy_selection(
            Function<EmbeddingModel, MmrContentAggregator> aggregatorProvider) {

        // given
        Query query = Query.from("What is AI?");

        Content content1 = Content.from("AI is artificial intelligence");
        Content content2 = Content.from("Machine learning is a subset of AI");
        Content content3 = Content.from("Deep learning uses neural networks");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2, content3)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // Mock query embedding
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        // Mock content embeddings with different similarities
        List<Embedding> contentEmbeddings = asList(
                Embedding.from(new float[] {0.9f, 0.1f, 0.0f}), // high similarity
                Embedding.from(new float[] {0.5f, 0.5f, 0.0f}), // medium similarity
                Embedding.from(new float[] {0.1f, 0.1f, 0.8f}) // low similarity, high diversity
                );

        List<TextSegment> textSegments = asList(content1.textSegment(), content2.textSegment(), content3.textSegment());
        when(embeddingModel.embedAll(textSegments)).thenReturn(Response.from(contentEmbeddings));

        MmrContentAggregator aggregator = aggregatorProvider.apply(embeddingModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(3);
        // MMR should balance relevance and diversity
        assertThat(aggregated.get(0)).isEqualTo(content1); // most relevant first
    }

    static Stream<Arguments> should_apply_mmr_with_auto_strategy_selection() {
        return Stream.<Arguments>builder()
                .add(Arguments.of((Function<EmbeddingModel, MmrContentAggregator>) MmrContentAggregator::new))
                .add(Arguments.of((Function<EmbeddingModel, MmrContentAggregator>)
                        (embeddingModel) -> MmrContentAggregator.builder()
                                .embeddingModel(embeddingModel)
                                .build()))
                .build();
    }

    @Test
    void should_use_manual_strategy_when_provided() {
        // given
        Query query = Query.from("test query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStrategy manualStrategy = new GenerateEmbeddings();

        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel, manualStrategy);

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings =
                asList(Embedding.from(new float[] {0.9f, 0.1f, 0.0f}), Embedding.from(new float[] {0.8f, 0.2f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(2);
    }

    @Test
    void should_apply_lambda_parameter_correctly() {
        // given
        Query query = Query.from("test");
        Content content1 = Content.from("very relevant content");
        Content content2 = Content.from("diverse but less relevant");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // Test with high lambda (favor relevance)
        MmrContentAggregator relevanceFocused = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .lambda(0.9) // heavily favor relevance
                .build();

        // Mock embeddings - content1 more relevant, content2 more diverse
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = asList(
                Embedding.from(new float[] {0.95f, 0.05f, 0.0f}), // high relevance
                Embedding.from(new float[] {0.1f, 0.1f, 0.8f}) // low relevance, high diversity
                );
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = relevanceFocused.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(2);
        assertThat(aggregated.get(0)).isEqualTo(content1); // should prioritize relevant content
    }

    @Test
    void should_fail_when_multiple_queries_with_default_query_selector() {
        // given
        Map<Query, Collection<List<Content>>> queryToContents = new HashMap<>();
        queryToContents.put(Query.from("query 1"), singletonList(asList()));
        queryToContents.put(Query.from("query 2"), singletonList(asList()));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel);

        // when-then
        assertThatThrownBy(() -> aggregator.aggregate(queryToContents))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queries, making MMR ambiguous")
                .hasMessageContaining("Please provide a 'querySelector'");
    }

    @Test
    void should_handle_multiple_queries_with_custom_query_selector() {
        // given
        Function<Map<Query, Collection<List<Content>>>, Query> querySelector =
                (q) -> q.keySet().iterator().next(); // select first query

        Query query1 = Query.from("primary query");
        Query query2 = Query.from("secondary query");

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");

        Map<Query, Collection<List<Content>>> queryToContents = new LinkedHashMap<>();
        queryToContents.put(query1, singletonList(asList(content1)));
        queryToContents.put(query2, singletonList(asList(content2)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .querySelector(querySelector)
                .build();

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query1.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings =
                asList(Embedding.from(new float[] {0.9f, 0.1f, 0.0f}), Embedding.from(new float[] {0.8f, 0.2f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(2); // fused contents from both queries
    }

    @Test
    void should_filter_by_min_score() {
        // given
        Query query = Query.from("test query");
        Content content1 = Content.from("highly relevant");
        Content content2 = Content.from("somewhat relevant");
        Content content3 = Content.from("not relevant");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2, content3)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .minScore(0.5) // filter out low scores
                .build();

        // Mock embeddings with different similarity scores
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = asList(
                Embedding.from(new float[] {0.9f, 0.1f, 0.0f}), // score ~0.9 (above threshold)
                Embedding.from(new float[] {0.7f, 0.3f, 0.0f}), // score ~0.7 (above threshold)
                Embedding.from(new float[] {0.2f, 0.8f, 0.0f}) // score ~0.2 (below threshold)
                );
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        assertThat(aggregated)
                // then
                .hasSize(2)
                // content3 should be filtered out
                .containsExactly(content1, content2);
    }

    @Test
    void should_limit_max_results() {
        // given
        Query query = Query.from("test query");
        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        Content content3 = Content.from("content 3");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2, content3)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .build();

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = asList(
                Embedding.from(new float[] {0.9f, 0.1f, 0.0f}),
                Embedding.from(new float[] {0.8f, 0.2f, 0.0f}),
                Embedding.from(new float[] {0.7f, 0.3f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(2); // limited by maxResults
    }

    @ParameterizedTest
    @MethodSource
    void should_return_empty_list_when_no_content(Map<Query, Collection<List<Content>>> queryToContents) {
        // given
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel);

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).isEmpty();
        verifyNoInteractions(embeddingModel);
    }

    private static Stream<Arguments> should_return_empty_list_when_no_content() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(emptyMap()))
                .add(Arguments.of(singletonMap(Query.from("query"), emptyList())))
                .add(Arguments.of(singletonMap(Query.from("query"), singletonList(emptyList()))))
                .add(Arguments.of(singletonMap(Query.from("query"), asList(emptyList(), emptyList()))))
                .build();
    }

    @Test
    void should_use_force_embedding_generation() {
        // given
        Query query = Query.from("test query");
        Content content1 = Content.from("content 1");

        Map<Query, Collection<List<Content>>> queryToContents = singletonMap(query, singletonList(asList(content1)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .forceEmbeddingGeneration(true)
                .build();

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = singletonList(Embedding.from(new float[] {0.9f, 0.1f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(1);
        assertThat(aggregated.get(0)).isEqualTo(content1);
    }

    @Test
    void should_warn_when_both_manual_strategy_and_force_generation_provided() {
        // given
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStrategy manualStrategy = new UseExistingEmbeddings();

        // when/then - should not throw exception but log warning
        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .forceEmbeddingGeneration(true)
                .strategy(manualStrategy)
                .build();

        // Verify aggregator was created successfully
        assertThat(aggregator).isNotNull();
    }

    @Test
    void should_handle_builder_with_all_parameters() {
        // given
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        Function<Map<Query, Collection<List<Content>>>, Query> querySelector =
                (q) -> q.keySet().iterator().next();
        EmbeddingStrategy strategy = new HybridEmbeddings();

        // when
        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .querySelector(querySelector)
                .minScore(0.3)
                .maxResults(5)
                .lambda(0.8)
                .strategy(strategy)
                .build();

        // then
        assertThat(aggregator).isNotNull();
    }

    @Test
    void should_use_existing_embeddings_strategy() {
        // given - Create content with existing embeddings
        Query query = Query.from("test query");

        // Create TextSegments with embedding metadata
        TextSegment segment1 = TextSegment.from("content with embedding 1");
        TextSegment segment2 = TextSegment.from("content with embedding 2");

        Content content1 = Content.from(segment1);
        Content content2 = Content.from(segment2);

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(content1, content2)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        UseExistingEmbeddings strategy = new UseExistingEmbeddings();

        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel, strategy);

        // Mock existing embeddings in metadata
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        Embedding docEmbedding1 = Embedding.from(new float[] {0.9f, 0.1f, 0.0f});
        Embedding docEmbedding2 = Embedding.from(new float[] {0.8f, 0.2f, 0.0f});

        try (MockedStatic<EmbeddingMetadataUtils> mockedUtils = Mockito.mockStatic(EmbeddingMetadataUtils.class)) {
            // Mock query embedding extraction
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractQueryEmbedding(segment1))
                    .thenReturn(queryEmbedding);

            // Mock document embedding extraction
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractDocumentEmbedding(segment1))
                    .thenReturn(docEmbedding1);
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractDocumentEmbedding(segment2))
                    .thenReturn(docEmbedding2);

            // when
            List<Content> aggregated = aggregator.aggregate(queryToContents);

            assertThat(aggregated)
                    // then
                    .hasSize(2)
                    .containsExactly(content1, content2);
        }
    }

    @Test
    void should_use_hybrid_embeddings_strategy() {
        // given - Mixed content (some with embeddings, some without)
        Query query = Query.from("hybrid test");

        TextSegment segmentWithEmbedding = TextSegment.from("content with embedding");
        TextSegment segmentWithoutEmbedding = TextSegment.from("content without embedding");

        Content contentWithEmbedding = Content.from(segmentWithEmbedding);
        Content contentWithoutEmbedding = Content.from(segmentWithoutEmbedding);

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(asList(contentWithEmbedding, contentWithoutEmbedding)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        HybridEmbeddings strategy = new HybridEmbeddings();

        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel, strategy);

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        Embedding existingEmbedding = Embedding.from(new float[] {0.9f, 0.1f, 0.0f});
        Embedding generatedEmbedding = Embedding.from(new float[] {0.8f, 0.2f, 0.0f});

        // Mock embedding model for content without embedding
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));
        when(embeddingModel.embedAll(singletonList(segmentWithoutEmbedding)))
                .thenReturn(Response.from(singletonList(generatedEmbedding)));

        try (MockedStatic<EmbeddingMetadataUtils> mockedUtils = Mockito.mockStatic(EmbeddingMetadataUtils.class)) {
            // First content has embedding, second doesn't
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractDocumentEmbedding(segmentWithEmbedding))
                    .thenReturn(existingEmbedding);
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractDocumentEmbedding(segmentWithoutEmbedding))
                    .thenReturn(null);

            // Query embedding available in first content
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractQueryEmbedding(segmentWithEmbedding))
                    .thenReturn(queryEmbedding);
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractQueryEmbedding(segmentWithoutEmbedding))
                    .thenReturn(null);

            // when
            List<Content> aggregated = aggregator.aggregate(queryToContents);

            assertThat(aggregated)
                    // then
                    .hasSize(2)
                    .containsExactlyInAnyOrder(contentWithEmbedding, contentWithoutEmbedding);
        }
    }

    @Test
    void should_fail_when_using_existing_strategy_without_query_embedding() {
        // given
        Query query = Query.from("test");
        Content content = Content.from("content");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(singletonList(content)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        UseExistingEmbeddings strategy = new UseExistingEmbeddings();

        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel, strategy);

        try (MockedStatic<EmbeddingMetadataUtils> mockedUtils = Mockito.mockStatic(EmbeddingMetadataUtils.class)) {
            // Mock no query embedding available
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractQueryEmbedding(any()))
                    .thenReturn(null);

            // when-then
            assertThatThrownBy(() -> aggregator.aggregate(queryToContents))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Query embedding not found in content metadata");
        }
    }

    @Test
    void should_fail_when_using_existing_strategy_without_document_embedding() {
        // given
        Query query = Query.from("test");
        TextSegment segment = TextSegment.from("content without doc embedding");
        Content content = Content.from(segment);

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(singletonList(content)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        UseExistingEmbeddings strategy = new UseExistingEmbeddings();

        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel, strategy);

        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});

        try (MockedStatic<EmbeddingMetadataUtils> mockedUtils = Mockito.mockStatic(EmbeddingMetadataUtils.class)) {
            // Mock query embedding available but no document embedding
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractQueryEmbedding(segment))
                    .thenReturn(queryEmbedding);
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractDocumentEmbedding(segment))
                    .thenReturn(null);

            // when-then
            assertThatThrownBy(() -> aggregator.aggregate(queryToContents))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Content must have document embedding for MMR processing");
        }
    }

    @Test
    void should_handle_hybrid_strategy_when_query_embedding_not_in_existing_content() {
        // given
        Query query = Query.from("test query");
        TextSegment segment = TextSegment.from("content");
        Content content = Content.from(segment);

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(singletonList(content)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        HybridEmbeddings strategy = new HybridEmbeddings();

        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel, strategy);

        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        Embedding docEmbedding = Embedding.from(new float[] {0.9f, 0.1f, 0.0f});

        // Mock embedding generation for query
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        try (MockedStatic<EmbeddingMetadataUtils> mockedUtils = Mockito.mockStatic(EmbeddingMetadataUtils.class)) {
            // No query embedding in metadata - will generate
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractQueryEmbedding(segment))
                    .thenReturn(null);
            // But document embedding exists
            mockedUtils
                    .when(() -> EmbeddingMetadataUtils.extractDocumentEmbedding(segment))
                    .thenReturn(docEmbedding);

            // when
            List<Content> aggregated = aggregator.aggregate(queryToContents);

            // then
            assertThat(aggregated).hasSize(1);
            assertThat(aggregated.get(0)).isEqualTo(content);
        }
    }

    @Test
    void should_test_different_constructor_variants() {
        // given
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // Test constructor with lambda only
        MmrContentAggregator aggregator1 = new MmrContentAggregator(embeddingModel, 0.8);
        assertThat(aggregator1).isNotNull();

        // Test constructor with force generation
        MmrContentAggregator aggregator2 = new MmrContentAggregator(embeddingModel, true);
        assertThat(aggregator2).isNotNull();
    }

    @Test
    void should_handle_scoring_model_warning() {
        // given
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ScoringModel scoringModel = mock(ScoringModel.class);

        // when - should log warning but not fail
        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .scoringModel(scoringModel)
                .build();

        // then
        assertThat(aggregator).isNotNull();
    }

    @Test
    void should_handle_large_max_results_without_overflow() {
        // given
        Query query = Query.from("test");
        Content content = Content.from("content");

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(singletonList(content)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        // Test with Integer.MAX_VALUE to trigger overflow protection
        MmrContentAggregator aggregator = MmrContentAggregator.builder()
                .embeddingModel(embeddingModel)
                .maxResults(Integer.MAX_VALUE)
                .build();

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = singletonList(Embedding.from(new float[] {0.9f, 0.1f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when - should not cause overflow in warning calculation
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(1);
    }

    @Test
    void should_handle_empty_content_list_in_existing_strategy() {
        // given
        UseExistingEmbeddings strategy = new UseExistingEmbeddings();
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        Query query = Query.from("test");

        // when-then
        assertThatThrownBy(() -> strategy.processQueryEmbedding(query, List.of(), embeddingModel))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot extract query embedding from empty content list");
    }

    @Test
    void should_test_embedding_id_generation_with_metadata() {
        // given
        Query query = Query.from("test");

        // Create content with embedding ID in metadata using langchain4j way
        TextSegment segment = TextSegment.from("content");
        segment.metadata().put(String.valueOf(ContentMetadata.EMBEDDING_ID), "custom-id-123");
        Content content = Content.from(segment);

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(singletonList(content)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel);

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = singletonList(Embedding.from(new float[] {0.9f, 0.1f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(1);
    }

    @Test
    void should_test_embedding_id_generation_with_blank_metadata() {
        // given
        Query query = Query.from("test");

        // Create content with blank embedding ID in metadata
        TextSegment segment = TextSegment.from("content");
        segment.metadata().put(String.valueOf(ContentMetadata.EMBEDDING_ID), "   ");
        Content content = Content.from(segment);

        Map<Query, Collection<List<Content>>> queryToContents =
                singletonMap(query, singletonList(singletonList(content)));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        MmrContentAggregator aggregator = new MmrContentAggregator(embeddingModel);

        // Mock embeddings
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(query.text())).thenReturn(Response.from(queryEmbedding));

        List<Embedding> contentEmbeddings = singletonList(Embedding.from(new float[] {0.9f, 0.1f, 0.0f}));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(contentEmbeddings));

        // when
        List<Content> aggregated = aggregator.aggregate(queryToContents);

        // then
        assertThat(aggregated).hasSize(1);
    }
}
