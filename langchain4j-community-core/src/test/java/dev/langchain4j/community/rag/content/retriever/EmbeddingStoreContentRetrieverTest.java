package dev.langchain4j.community.rag.content.retriever;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.rag.content.util.EmbeddingMetadataUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddingStoreContentRetrieverTest {

    private static EmbeddingStore<TextSegment> EMBEDDING_STORE;

    private static EmbeddingModel EMBEDDING_MODEL;
    private static final Embedding EMBEDDING = Embedding.from(asList(1f, 2f, 3f));

    private static final Query QUERY = Query.from("query");

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final int CUSTOM_MAX_RESULTS = 1;

    private static final double CUSTOM_MIN_SCORE = 0.7;
    public static final double DEFAULT_MIN_SCORE = 0.0;

    @BeforeEach
    void beforeEach() {
        EMBEDDING_STORE = mock(EmbeddingStore.class);
        when(EMBEDDING_STORE.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(asList(
                        new EmbeddingMatch<>(0.9, "id 1", EMBEDDING, TextSegment.from("content 1")),
                        new EmbeddingMatch<>(0.7, "id 2", EMBEDDING, TextSegment.from("content 2")))));

        EMBEDDING_MODEL = mock(EmbeddingModel.class);
        when(EMBEDDING_MODEL.embed(anyString())).thenReturn(Response.from(EMBEDDING));
    }

    @Test
    void should_retrieve() {

        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL);

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_maxResults() {

        // given
        ContentRetriever contentRetriever =
                new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL, CUSTOM_MAX_RESULTS);

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(CUSTOM_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_maxResults_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .maxResults(CUSTOM_MAX_RESULTS)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(CUSTOM_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_dynamicMaxResults_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicMaxResults((query) -> CUSTOM_MAX_RESULTS)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(CUSTOM_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_minScore_ctor() {

        // given
        ContentRetriever contentRetriever =
                new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL, null, CUSTOM_MIN_SCORE);

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(CUSTOM_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_minScore_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .minScore(CUSTOM_MIN_SCORE)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(CUSTOM_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_dynamicMinScore_builder() {

        // given
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicMinScore((query) -> CUSTOM_MIN_SCORE)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(CUSTOM_MIN_SCORE)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_filter() {

        // given
        Filter metadataFilter = metadataKey("key").isEqualTo("value");

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .filter(metadataFilter)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .filter(metadataFilter)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_retrieve_with_custom_dynamicFilter() {

        // given
        Filter metadataFilter = metadataKey("key").isEqualTo("value");

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(EMBEDDING_STORE)
                .embeddingModel(EMBEDDING_MODEL)
                .dynamicFilter((query) -> metadataFilter)
                .build();

        // when
        contentRetriever.retrieve(QUERY);

        // then
        verify(EMBEDDING_STORE)
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(EMBEDDING)
                        .maxResults(DEFAULT_MAX_RESULTS)
                        .minScore(DEFAULT_MIN_SCORE)
                        .filter(metadataFilter)
                        .build());
        verifyNoMoreInteractions(EMBEDDING_STORE);
        verify(EMBEDDING_MODEL).embed(QUERY.text());
        verifyNoMoreInteractions(EMBEDDING_MODEL);
    }

    @Test
    void should_include_explicit_display_name_in_to_string() {

        // given
        double minScore = 0.7;
        String displayName = "MyName";
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .displayName(displayName)
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(minScore)
                .build();

        // when
        String result = contentRetriever.toString();

        // then
        assertThat(result).contains(displayName);
    }

    @Test
    void should_include_implicit_display_name_in_to_string() {

        // given
        double minScore = 0.7;
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(minScore)
                .build();

        // when
        String result = contentRetriever.toString();

        // then
        assertThat(result).contains(EmbeddingStoreContentRetriever.DEFAULT_DISPLAY_NAME);
    }

    // New tests
    @Test
    void should_enrich_segments_with_embeddings_in_metadata() {
        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL);

        // when
        var contents = contentRetriever.retrieve(QUERY);

        // then
        assertThat(contents).hasSize(2);

        // Check first segment has embeddings in metadata
        var firstContent = contents.get(0);
        assertThat(firstContent.textSegment().metadata()).isNotNull();
        assertThat(firstContent.textSegment().metadata().toMap()).containsKey("embedding");
        assertThat(firstContent.textSegment().metadata().toMap()).containsKey("queryEmbedding");

        // Check second segment has embeddings in metadata
        var secondContent = contents.get(1);
        assertThat(secondContent.textSegment().metadata()).isNotNull();
        assertThat(secondContent.textSegment().metadata().toMap()).containsKey("embedding");
        assertThat(secondContent.textSegment().metadata().toMap()).containsKey("queryEmbedding");
    }

    @Test
    void should_extract_embeddings_from_metadata() {
        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL);

        // when
        var contents = contentRetriever.retrieve(QUERY);

        // then
        var enrichedSegment = contents.get(0).textSegment();

        // Extract embeddings using utility methods
        var extractedDocumentEmbedding = EmbeddingMetadataUtils.extractDocumentEmbedding(enrichedSegment);
        var extractedQueryEmbedding = EmbeddingMetadataUtils.extractQueryEmbedding(enrichedSegment);

        // Verify embeddings are not null and have correct dimensions
        assertThat(extractedDocumentEmbedding).isNotNull();
        assertThat(extractedQueryEmbedding).isNotNull();
        assertThat(extractedQueryEmbedding.vector()).isEqualTo(EMBEDDING.vector());
    }

    @Test
    void should_detect_embeddings_presence_in_metadata() {
        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL);

        // when
        var contents = contentRetriever.retrieve(QUERY);

        // then
        var enrichedSegment = contents.get(0).textSegment();

        // Check utility methods for detecting embeddings
        assertThat(EmbeddingMetadataUtils.hasDocumentEmbedding(enrichedSegment)).isTrue();
        assertThat(EmbeddingMetadataUtils.hasQueryEmbedding(enrichedSegment)).isTrue();
    }

    @Test
    void should_handle_segments_without_embeddings_in_metadata() {
        // given
        var plainSegment = TextSegment.from("plain content without embeddings");

        // when & then
        assertThat(EmbeddingMetadataUtils.hasDocumentEmbedding(plainSegment)).isFalse();
        assertThat(EmbeddingMetadataUtils.hasQueryEmbedding(plainSegment)).isFalse();
        assertThat(EmbeddingMetadataUtils.extractDocumentEmbedding(plainSegment)).isNull();
        assertThat(EmbeddingMetadataUtils.extractQueryEmbedding(plainSegment)).isNull();
    }

    @Test
    void should_store_embeddings_as_base64_strings_in_metadata() {
        // given
        ContentRetriever contentRetriever = new EmbeddingStoreContentRetriever(EMBEDDING_STORE, EMBEDDING_MODEL);

        // when
        var contents = contentRetriever.retrieve(QUERY);

        // then
        var enrichedSegment = contents.get(0).textSegment();
        var metadata = enrichedSegment.metadata().toMap();

        // Verify embeddings are stored as base64 strings
        assertThat(metadata.get("embedding")).isInstanceOf(String.class);
        assertThat(metadata.get("queryEmbedding")).isInstanceOf(String.class);

        // Verify base64 strings are not empty
        String documentEmbeddingBase64 = (String) metadata.get("embedding");
        String queryEmbeddingBase64 = (String) metadata.get("queryEmbedding");

        assertThat(documentEmbeddingBase64).isNotEmpty();
        assertThat(queryEmbeddingBase64).isNotEmpty();

        // Verify they can be decoded back to embeddings
        var reconstructedQuery = EmbeddingMetadataUtils.extractQueryEmbedding(enrichedSegment);
        assertThat(reconstructedQuery.vector()).isEqualTo(EMBEDDING.vector());
    }
}
