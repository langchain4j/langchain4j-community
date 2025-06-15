package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.store.cache.EmbeddingCache;
import dev.langchain4j.data.embedding.Embedding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the TestingEmbeddingCache class.
 */
class TestingEmbeddingCacheTest {

    @Mock
    private EmbeddingCache mockCache;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @Test
    void should_store_in_both_namespaces_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";
        Embedding embedding = mock(Embedding.class);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        cache.put(text, embedding);

        // then
        verify(mockCache).put(text, embedding); // Regular namespace
        verify(mockCache).put("test:" + testContextId + ":" + text, embedding); // Test namespace

        closeable.close();
    }

    @Test
    void should_not_store_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";
        Embedding embedding = mock(Embedding.class);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        cache.put(text, embedding);

        // then - should not store anything in play mode
        verify(mockCache, never()).put(text, embedding);
        verify(mockCache, never()).put("test:" + testContextId + ":" + text, embedding);

        closeable.close();
    }

    @Test
    void should_check_test_namespace_first_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";
        Embedding testEmbedding = mock(Embedding.class);
        Embedding regularEmbedding = mock(Embedding.class);

        when(mockCache.get("test:" + testContextId + ":" + text)).thenReturn(Optional.of(testEmbedding));
        when(mockCache.get(text)).thenReturn(Optional.of(regularEmbedding));

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        Optional<Embedding> result = cache.get(text);

        // then - should return the test embedding, not the regular one
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(testEmbedding);

        closeable.close();
    }

    @Test
    void should_fallback_to_regular_namespace_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";
        Embedding regularEmbedding = mock(Embedding.class);

        when(mockCache.get("test:" + testContextId + ":" + text)).thenReturn(Optional.empty());
        when(mockCache.get(text)).thenReturn(Optional.of(regularEmbedding));

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        Optional<Embedding> result = cache.get(text);

        // then - should fall back to the regular embedding
        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(regularEmbedding);

        closeable.close();
    }

    @Test
    void should_use_regular_namespace_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";
        Embedding regularEmbedding = mock(Embedding.class);

        when(mockCache.get(text)).thenReturn(Optional.of(regularEmbedding));

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        Optional<Embedding> result = cache.get(text);

        // then - should return the regular embedding
        assertThat(result).isPresent();
        assertThat(result).containsSame(regularEmbedding);

        closeable.close();
    }

    @Test
    void should_multi_remove_from_both_namespaces_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";
        String testKey = "test:" + testContextId + ":" + text;

        when(mockCache.remove(text)).thenReturn(true);
        when(mockCache.remove(testKey)).thenReturn(false);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        boolean result = cache.remove(text);

        // then - should attempt to remove from both namespaces
        verify(mockCache).remove(text);
        verify(mockCache).remove(testKey);
        assertThat(result).isTrue(); // Should return true if either remove operation succeeded

        closeable.close();
    }

    @Test
    void should_return_true_if_either_remove_succeeds_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";

        // Test various combinations of removal results
        when(mockCache.remove(text)).thenReturn(false);
        when(mockCache.remove("test:" + testContextId + ":" + text)).thenReturn(true);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        boolean result = cache.remove(text);

        // then - should return true if either removal succeeds
        assertThat(result).isTrue();

        closeable.close();
    }

    @Test
    void should_return_false_if_both_removes_fail_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";

        when(mockCache.remove(text)).thenReturn(false);
        when(mockCache.remove("test:" + testContextId + ":" + text)).thenReturn(false);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        boolean result = cache.remove(text);

        // then - should return false if both removal operations failed
        assertThat(result).isFalse();

        closeable.close();
    }

    @Test
    void should_not_remove_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text = "test embedding text";

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        boolean result = cache.remove(text);

        // then - should not attempt to remove anything in play mode
        verify(mockCache, never()).remove(text);
        verify(mockCache, never()).remove("test:" + testContextId + ":" + text);
        assertThat(result).isFalse();

        closeable.close();
    }

    @Test
    void should_handle_get_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text1 = "test embedding 1";
        String text2 = "test embedding 2";
        Embedding testEmbedding1 = mock(Embedding.class);
        Embedding regularEmbedding2 = mock(Embedding.class);

        // Mock test namespace has embedding for text1
        // Regular namespace has embedding for text2
        when(mockCache.get("test:" + testContextId + ":" + text1)).thenReturn(Optional.of(testEmbedding1));
        when(mockCache.get(text1)).thenReturn(Optional.empty());
        when(mockCache.get("test:" + testContextId + ":" + text2)).thenReturn(Optional.empty());
        when(mockCache.get(text2)).thenReturn(Optional.of(regularEmbedding2));

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        Map<String, Embedding> results = cache.get(List.of(text1, text2));

        // then - should return embeddings from both namespaces based on availability
        assertThat(results).hasSize(2);
        assertThat(results.get(text1)).isSameAs(testEmbedding1); // From test namespace
        assertThat(results.get(text2)).isSameAs(regularEmbedding2); // From regular namespace

        closeable.close();
    }

    @Test
    void should_handle_put_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text1 = "test embedding 1";
        String text2 = "test embedding 2";
        Embedding embedding1 = mock(Embedding.class);
        Embedding embedding2 = mock(Embedding.class);

        Map<String, Embedding> embeddings = new HashMap<>();
        embeddings.put(text1, embedding1);
        embeddings.put(text2, embedding2);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        cache.put(embeddings);

        // then - should store in both regular and test namespaces
        Map<String, Embedding> testEmbeddings = new HashMap<>();
        testEmbeddings.put("test:" + testContextId + ":" + text1, embedding1);
        testEmbeddings.put("test:" + testContextId + ":" + text2, embedding2);

        // Verify regular cache operations
        verify(mockCache).put(embeddings);

        // Verify test cache operations with transformed keys
        verify(mockCache).put(testEmbeddings);

        closeable.close();
    }

    @Test
    void should_not_put_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text1 = "test embedding 1";
        String text2 = "test embedding 2";
        Embedding embedding1 = mock(Embedding.class);
        Embedding embedding2 = mock(Embedding.class);

        Map<String, Embedding> embeddings = new HashMap<>();
        embeddings.put(text1, embedding1);
        embeddings.put(text2, embedding2);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        cache.put(embeddings);

        // then - should not modify cache in play mode
        verify(mockCache, never()).put(embeddings);

        closeable.close();
    }

    @Test
    void should_exists_check_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text1 = "test embedding 1";
        String text2 = "test embedding 2";

        Map<String, Boolean> testResults = new HashMap<>();
        testResults.put("test:" + testContextId + ":" + text1, true);
        testResults.put("test:" + testContextId + ":" + text2, false);

        // Set up mock to return test namespace results
        when(mockCache.exists(List.of("test:" + testContextId + ":" + text1)))
                .thenReturn(Map.of("test:" + testContextId + ":" + text1, true));
        when(mockCache.exists(List.of("test:" + testContextId + ":" + text2)))
                .thenReturn(Map.of("test:" + testContextId + ":" + text2, false));

        // Set up mock to return regular namespace results for text2
        when(mockCache.get(text2)).thenReturn(Optional.of(mock(Embedding.class)));

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        Map<String, Boolean> results = cache.exists(List.of(text1, text2));

        // then - should check both namespaces
        assertThat(results).hasSize(2);
        assertThat(results.get(text1)).isTrue(); // Found in test namespace
        assertThat(results.get(text2)).isTrue(); // Found in regular namespace

        closeable.close();
    }

    @Test
    void should_remove_from_both_namespaces_in_record_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text1 = "test embedding 1";
        String text2 = "test embedding 2";
        String testKey1 = "test:" + testContextId + ":" + text1;
        String testKey2 = "test:" + testContextId + ":" + text2;

        // First text exists in regular namespace only, second text exists in test namespace only
        when(mockCache.remove(text1)).thenReturn(true);
        when(mockCache.remove(testKey1)).thenReturn(false);
        when(mockCache.remove(text2)).thenReturn(false);
        when(mockCache.remove(testKey2)).thenReturn(true);

        TestingEmbeddingCache cache = TestingEmbeddingCache.inRecordMode(mockCache, testContextId);

        // when
        Map<String, Boolean> results = cache.remove(List.of(text1, text2));

        // then - should remove from both namespaces
        verify(mockCache).remove(text1);
        verify(mockCache).remove(testKey1);
        verify(mockCache).remove(text2);
        verify(mockCache).remove(testKey2);

        // Both should return true since at least one removal succeeded for each text
        assertThat(results).hasSize(2);
        assertThat(results.get(text1)).isTrue();
        assertThat(results.get(text2)).isTrue();

        closeable.close();
    }

    @Test
    void should_not_multi_remove_in_play_mode() throws Exception {
        // given
        String testContextId = "test-123";
        String text1 = "test embedding 1";
        String text2 = "test embedding 2";

        TestingEmbeddingCache cache = TestingEmbeddingCache.inPlayMode(mockCache, testContextId);

        // when
        Map<String, Boolean> results = cache.remove(List.of(text1, text2));

        // then - should not attempt to remove anything in play mode
        verify(mockCache, never()).remove(text1);
        verify(mockCache, never()).remove(text2);
        verify(mockCache, never()).remove("test:" + testContextId + ":" + text1);
        verify(mockCache, never()).remove("test:" + testContextId + ":" + text2);
        verify(mockCache, never()).remove(List.of(text1, text2));

        assertThat(results).hasSize(2);
        assertThat(results.get(text1)).isFalse();
        assertThat(results.get(text2)).isFalse();

        closeable.close();
    }
}
