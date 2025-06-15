package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.store.cache.EmbeddingCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachedEmbeddingModelTest {

    private static final String TEST_TEXT = "Hello, world!";
    private static final Embedding TEST_EMBEDDING = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
    private static final TextSegment TEST_SEGMENT = TextSegment.from(TEST_TEXT);

    private EmbeddingModel delegate;
    private EmbeddingCache cache;
    private CachedEmbeddingModel cachedModel;

    @BeforeEach
    void setUp() {
        delegate = mock(EmbeddingModel.class);
        cache = mock(EmbeddingCache.class);
        cachedModel = new CachedEmbeddingModel(delegate, cache);
    }

    @Test
    void should_return_cached_embedding_when_available() {
        // Given
        when(cache.get(TEST_TEXT)).thenReturn(Optional.of(TEST_EMBEDDING));

        // When
        Response<Embedding> response = cachedModel.embed(TEST_TEXT);

        // Then
        assertThat(response.content()).isEqualTo(TEST_EMBEDDING);
        verify(cache).get(TEST_TEXT);
        verify(delegate, never()).embed(TEST_TEXT);
    }

    @Test
    void should_use_delegate_and_update_cache_on_cache_miss() {
        // Given
        when(cache.get(TEST_TEXT)).thenReturn(Optional.empty());
        when(delegate.embed(TEST_SEGMENT)).thenReturn(Response.from(TEST_EMBEDDING));

        // When
        Response<Embedding> response = cachedModel.embed(TEST_TEXT);

        // Then
        assertThat(response.content()).isEqualTo(TEST_EMBEDDING);
        verify(cache).get(TEST_TEXT);
        verify(delegate).embed(TEST_SEGMENT);
        verify(cache).put(TEST_TEXT, TEST_EMBEDDING);
    }

    @Test
    void should_handle_mix_of_cache_hits_and_misses_in_embedAll() {
        // Given
        String text1 = "Text one";
        String text2 = "Text two";
        String text3 = "Text three";

        TextSegment segment1 = TextSegment.from(text1);
        TextSegment segment2 = TextSegment.from(text2);
        TextSegment segment3 = TextSegment.from(text3);

        Embedding embedding1 = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
        Embedding embedding2 = new Embedding(new float[] {0.4f, 0.5f, 0.6f});
        Embedding embedding3 = new Embedding(new float[] {0.7f, 0.8f, 0.9f});

        List<TextSegment> segments = Arrays.asList(segment1, segment2, segment3);

        // Cache hit for first and third segments
        when(cache.get(text1)).thenReturn(Optional.of(embedding1));
        when(cache.get(text2)).thenReturn(Optional.empty());
        when(cache.get(text3)).thenReturn(Optional.of(embedding3));

        // Delegate response for the cache miss (text2)
        TokenUsage tokenUsage = new TokenUsage(10, 0, 10);
        when(delegate.embedAll(List.of(segment2))).thenReturn(Response.from(List.of(embedding2), tokenUsage));

        // When
        Response<List<Embedding>> response = cachedModel.embedAll(segments);

        // Then
        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0)).isEqualTo(embedding1);
        assertThat(response.content().get(1)).isEqualTo(embedding2);
        assertThat(response.content().get(2)).isEqualTo(embedding3);

        // Verify token usage from delegate is maintained
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(10);

        // Verify delegate called only for cache miss
        verify(delegate).embedAll(List.of(segment2));
        verify(cache).put(text2, embedding2);
    }

    @Test
    void should_return_embeddings_with_no_token_usage_when_all_cache_hits() {
        // Given
        String text1 = "Text one";
        String text2 = "Text two";

        TextSegment segment1 = TextSegment.from(text1);
        TextSegment segment2 = TextSegment.from(text2);

        Embedding embedding1 = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
        Embedding embedding2 = new Embedding(new float[] {0.4f, 0.5f, 0.6f});

        List<TextSegment> segments = Arrays.asList(segment1, segment2);

        // Cache hits for all segments
        when(cache.get(text1)).thenReturn(Optional.of(embedding1));
        when(cache.get(text2)).thenReturn(Optional.of(embedding2));

        // When
        Response<List<Embedding>> response = cachedModel.embedAll(segments);

        // Then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0)).isEqualTo(embedding1);
        assertThat(response.content().get(1)).isEqualTo(embedding2);

        // No token usage for all cache hits
        assertThat(response.tokenUsage()).isNull();

        // Verify delegate never called
        verify(delegate, never()).embedAll(segments);
    }

    @Test
    void should_clear_cache_when_clearCache_called() {
        // When
        cachedModel.clearCache();

        // Then
        verify(cache).clear();
    }
}
