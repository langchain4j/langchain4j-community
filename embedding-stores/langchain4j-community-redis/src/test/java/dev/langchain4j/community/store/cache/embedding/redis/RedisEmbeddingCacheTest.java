package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

class RedisEmbeddingCacheTest {

    private static final String TEST_PREFIX = "test:";
    private static final String TEST_TEXT = "Hello, world!";
    private static final Embedding TEST_EMBEDDING = new Embedding(new float[] {0.1f, 0.2f, 0.3f});

    private JedisPooled jedis;
    private RedisEmbeddingCache cache;
    private RedisEmbeddingCache cacheWithTtl;

    @BeforeEach
    void setUp() {
        jedis = mock(JedisPooled.class);
        cache = new RedisEmbeddingCache(jedis, TEST_PREFIX);
        cacheWithTtl = new RedisEmbeddingCache(jedis, TEST_PREFIX, 100, 3600);
    }

    @Test
    void should_return_empty_for_null_or_empty_text() {
        // When
        Optional<Embedding> result1 = cache.get(null);
        Optional<Embedding> result2 = cache.get("");

        // Then
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
        verify(jedis, never()).get(anyString());
    }

    @Test
    void should_put_and_get_embedding() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);

        // Mock JSON response for jsonGet
        String jsonResponse =
                "{\"text\":\"Hello, world!\",\"embedding\":{\"vector\":[0.1,0.2,0.3]},\"metadata\":{},\"modelName\":null,\"insertedAt\":\"2025-05-03T03:52:51.754309Z\",\"accessedAt\":\"2025-05-03T03:52:51.754310Z\",\"accessCount\":0}";
        when(jedis.jsonGet(key)).thenReturn(jsonResponse);

        // When
        cache.put(TEST_TEXT, TEST_EMBEDDING);
        Optional<Embedding> retrievedEmbedding = cache.get(TEST_TEXT);

        // Then
        verify(jedis).jsonSet(eq(key), eq(Path.ROOT_PATH), anyString());
        assertThat(retrievedEmbedding).isPresent();
        assertThat(retrievedEmbedding.get().vector()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void should_set_ttl_when_configured() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cacheWithTtl.md5(TEST_TEXT);

        // When
        cacheWithTtl.put(TEST_TEXT, TEST_EMBEDDING);

        // Then
        verify(jedis).jsonSet(eq(key), eq(Path.ROOT_PATH), anyString());
        verify(jedis).expire(eq(key), eq(3600L));
    }

    @Test
    void should_update_ttl_on_cache_hit_when_configured() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cacheWithTtl.md5(TEST_TEXT);
        // Mock JSON response for jsonGet
        String jsonResponse =
                "{\"text\":\"Hello, world!\",\"embedding\":{\"vector\":[0.1,0.2,0.3]},\"metadata\":{},\"modelName\":null,\"insertedAt\":\"2025-05-03T03:52:51.754309Z\",\"accessedAt\":\"2025-05-03T03:52:51.754310Z\",\"accessCount\":0}";

        when(jedis.jsonGet(key)).thenReturn(jsonResponse);

        // When
        Optional<Embedding> retrievedEmbedding = cacheWithTtl.get(TEST_TEXT);

        // Then
        assertThat(retrievedEmbedding).isPresent();
        verify(jedis).expire(eq(key), eq(3600L));
    }

    @Test
    void should_not_set_ttl_when_not_configured() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);

        // When
        cache.put(TEST_TEXT, TEST_EMBEDDING);

        // Then
        verify(jedis).jsonSet(eq(key), eq(Path.ROOT_PATH), anyString());
        verify(jedis, never()).expire(anyString(), anyLong());
    }

    @Test
    void should_remove_and_return_true_when_key_exists() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);
        when(jedis.del(key)).thenReturn(1L);

        // When
        boolean result = cache.remove(TEST_TEXT);

        // Then
        assertThat(result).isTrue();
        verify(jedis).del(key);
    }

    @Test
    void should_return_false_when_removing_non_existent_key() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);
        when(jedis.del(key)).thenReturn(0L);

        // When
        boolean result = cache.remove(TEST_TEXT);

        // Then
        assertThat(result).isFalse();
        verify(jedis).del(key);
    }

    @Test
    void should_not_put_null_or_empty_text() {
        // When
        cache.put(null, TEST_EMBEDDING);
        cache.put("", TEST_EMBEDDING);

        // Then
        verify(jedis, never()).set(anyString(), anyString());
    }

    @Test
    void should_not_put_null_embedding() {
        // When
        cache.put(TEST_TEXT, null);

        // Then
        verify(jedis, never()).set(anyString(), anyString());
    }

    @Test
    void should_not_remove_null_or_empty_text() {
        // When
        boolean result1 = cache.remove(null);
        boolean result2 = cache.remove("");

        // Then
        assertThat(result1).isFalse();
        assertThat(result2).isFalse();
        verify(jedis, never()).del(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_clear_all_keys_with_prefix() {
        // Given
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult()).thenReturn(Arrays.asList("key1", "key2", "key3"));
        when(scanResult.getCursor()).thenReturn("0");
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(scanResult);

        // When
        cache.clear();

        // Then
        verify(jedis).scan(eq("0"), any(ScanParams.class));
        verify(jedis).del(new String[] {"key1", "key2", "key3"});
    }

    @Test
    void should_return_empty_map_for_empty_or_null_mget_input() {
        // When
        Map<String, Embedding> result1 = cache.mget(null);
        Map<String, Embedding> result2 = cache.mget(List.of());

        // Then
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
    }

    @Test
    void should_mget_multiple_embeddings() {
        // Given
        String text1 = "Hello, world!";
        String text2 = "Goodbye, world!";
        String text3 = "Missing embedding";

        String key1 = TEST_PREFIX + "embedding:" + cache.md5(text1);
        String key2 = TEST_PREFIX + "embedding:" + cache.md5(text2);
        String key3 = TEST_PREFIX + "embedding:" + cache.md5(text3);

        // Mock Redis pipeline operations
        redis.clients.jedis.Pipeline pipeline = mock(redis.clients.jedis.Pipeline.class);
        redis.clients.jedis.Response<Object> jsonResp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Object> jsonResp2 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Object> jsonResp3 = mock(redis.clients.jedis.Response.class);

        // We also need to mock the legacy format responses for backward compatibility
        redis.clients.jedis.Response<String> legacyResp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<String> legacyResp2 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<String> legacyResp3 = mock(redis.clients.jedis.Response.class);

        when(jedis.pipelined()).thenReturn(pipeline);

        // Mock the jsonGet responses
        when(pipeline.jsonGet(key1)).thenReturn(jsonResp1);
        when(pipeline.jsonGet(key2)).thenReturn(jsonResp2);
        when(pipeline.jsonGet(key3)).thenReturn(jsonResp3);

        // Mock the legacy get responses
        when(pipeline.get(key1)).thenReturn(legacyResp1);
        when(pipeline.get(key2)).thenReturn(legacyResp2);
        when(pipeline.get(key3)).thenReturn(legacyResp3);

        // Mock the response values
        String jsonResponse1 =
                "{\"text\":\"Hello, world!\",\"embedding\":{\"vector\":[0.1,0.2,0.3]},\"metadata\":{},\"modelName\":null,\"insertedAt\":\"2025-05-03T03:52:51.754309Z\",\"accessedAt\":\"2025-05-03T03:52:51.754310Z\",\"accessCount\":0}";
        String jsonResponse2 =
                "{\"text\":\"Goodbye, world!\",\"embedding\":{\"vector\":[0.4,0.5,0.6]},\"metadata\":{},\"modelName\":null,\"insertedAt\":\"2025-05-03T03:52:51.754309Z\",\"accessedAt\":\"2025-05-03T03:52:51.754310Z\",\"accessCount\":0}";

        when(jsonResp1.get()).thenReturn(jsonResponse1);
        when(jsonResp2.get()).thenReturn(jsonResponse2);
        when(jsonResp3.get()).thenReturn(null);

        // Legacy responses are not used since JSON responses are available
        when(legacyResp1.get()).thenReturn(null);
        when(legacyResp2.get()).thenReturn(null);
        when(legacyResp3.get()).thenReturn(null);

        // When
        Map<String, Embedding> results = cache.mget(List.of(text1, text2, text3));

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).containsKeys(text1, text2);
        assertThat(results).doesNotContainKey(text3);

        Embedding embedding1 = results.get(text1);
        assertThat(embedding1.vector()).containsExactly(0.1f, 0.2f, 0.3f);

        Embedding embedding2 = results.get(text2);
        assertThat(embedding2.vector()).containsExactly(0.4f, 0.5f, 0.6f);
    }

    @Test
    void should_mput_multiple_embeddings() {
        // Given
        String text1 = "Hello, world!";
        String text2 = "Goodbye, world!";

        Embedding embedding1 = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
        Embedding embedding2 = new Embedding(new float[] {0.4f, 0.5f, 0.6f});

        String key1 = TEST_PREFIX + "embedding:" + cache.md5(text1);
        String key2 = TEST_PREFIX + "embedding:" + cache.md5(text2);

        Map<String, Embedding> embeddings = new HashMap<>();
        embeddings.put(text1, embedding1);
        embeddings.put(text2, embedding2);

        // Mock Redis pipeline operations
        redis.clients.jedis.Pipeline pipeline = mock(redis.clients.jedis.Pipeline.class);
        when(jedis.pipelined()).thenReturn(pipeline);

        // When
        cache.mput(embeddings);

        // Then
        verify(pipeline).jsonSet(eq(key1), eq(Path.ROOT_PATH), anyString());
        verify(pipeline).jsonSet(eq(key2), eq(Path.ROOT_PATH), anyString());
        verify(pipeline).sync();
    }

    @Test
    void should_mput_with_ttl_when_configured() {
        // Given
        String text1 = "Hello, world!";
        String text2 = "Goodbye, world!";

        Embedding embedding1 = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
        Embedding embedding2 = new Embedding(new float[] {0.4f, 0.5f, 0.6f});

        String key1 = TEST_PREFIX + "embedding:" + cacheWithTtl.md5(text1);
        String key2 = TEST_PREFIX + "embedding:" + cacheWithTtl.md5(text2);

        Map<String, Embedding> embeddings = new HashMap<>();
        embeddings.put(text1, embedding1);
        embeddings.put(text2, embedding2);

        // Mock Redis pipeline operations
        redis.clients.jedis.Pipeline pipeline = mock(redis.clients.jedis.Pipeline.class);
        when(jedis.pipelined()).thenReturn(pipeline);

        // When
        cacheWithTtl.mput(embeddings);

        // Then
        verify(pipeline).jsonSet(eq(key1), eq(Path.ROOT_PATH), anyString());
        verify(pipeline).jsonSet(eq(key2), eq(Path.ROOT_PATH), anyString());
        verify(pipeline).expire(eq(key1), eq(3600L));
        verify(pipeline).expire(eq(key2), eq(3600L));
        verify(pipeline).sync();
    }

    @Test
    void should_mexists_check_multiple_embeddings() {
        // Given
        String text1 = "Hello, world!";
        String text2 = "Goodbye, world!";
        String text3 = "Missing embedding";

        String key1 = TEST_PREFIX + "embedding:" + cache.md5(text1);
        String key2 = TEST_PREFIX + "embedding:" + cache.md5(text2);
        String key3 = TEST_PREFIX + "embedding:" + cache.md5(text3);

        // Mock Redis pipeline operations
        redis.clients.jedis.Pipeline pipeline = mock(redis.clients.jedis.Pipeline.class);
        redis.clients.jedis.Response<Boolean> resp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Boolean> resp2 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Boolean> resp3 = mock(redis.clients.jedis.Response.class);

        when(jedis.pipelined()).thenReturn(pipeline);
        when(pipeline.exists(key1)).thenReturn(resp1);
        when(pipeline.exists(key2)).thenReturn(resp2);
        when(pipeline.exists(key3)).thenReturn(resp3);

        when(resp1.get()).thenReturn(true);
        when(resp2.get()).thenReturn(true);
        when(resp3.get()).thenReturn(false);

        // When
        Map<String, Boolean> results = cache.mexists(List.of(text1, text2, text3));

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(text1)).isTrue();
        assertThat(results.get(text2)).isTrue();
        assertThat(results.get(text3)).isFalse();
    }

    @Test
    void should_mremove_multiple_embeddings() {
        // Given
        String text1 = "Hello, world!";
        String text2 = "Goodbye, world!";
        String text3 = "Missing embedding";

        String key1 = TEST_PREFIX + "embedding:" + cache.md5(text1);
        String key2 = TEST_PREFIX + "embedding:" + cache.md5(text2);
        String key3 = TEST_PREFIX + "embedding:" + cache.md5(text3);

        // Mock Redis pipeline operations
        redis.clients.jedis.Pipeline pipeline = mock(redis.clients.jedis.Pipeline.class);
        redis.clients.jedis.Response<Long> resp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Long> resp2 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Long> resp3 = mock(redis.clients.jedis.Response.class);

        when(jedis.pipelined()).thenReturn(pipeline);
        when(pipeline.del(key1)).thenReturn(resp1);
        when(pipeline.del(key2)).thenReturn(resp2);
        when(pipeline.del(key3)).thenReturn(resp3);

        when(resp1.get()).thenReturn(1L);
        when(resp2.get()).thenReturn(1L);
        when(resp3.get()).thenReturn(0L);

        // When
        Map<String, Boolean> results = cache.mremove(List.of(text1, text2, text3));

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(text1)).isTrue();
        assertThat(results.get(text2)).isTrue();
        assertThat(results.get(text3)).isFalse();
    }
}
