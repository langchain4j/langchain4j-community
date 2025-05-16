package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Tests for the metadata functionality in RedisEmbeddingCache.
 */
class RedisEmbeddingCacheMetadataTest {

    private static final String TEST_PREFIX = "test:";
    private static final String TEST_TEXT = "Hello, world!";
    private static final Embedding TEST_EMBEDDING = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
    private static final Map<String, Object> TEST_METADATA = createTestMetadata();
    private static final Path ROOT_PATH = Path.ROOT_PATH;

    private JedisPooled jedis;
    private RedisEmbeddingCache cache;
    private RedisEmbeddingCache cacheWithTtl;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jedis = mock(JedisPooled.class);
        cache = new RedisEmbeddingCache(jedis, TEST_PREFIX);
        cacheWithTtl = new RedisEmbeddingCache(jedis, TEST_PREFIX, 100, 3600);

        // Create the same object mapper as in the implementation
        objectMapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        com.fasterxml.jackson.databind.module.SimpleModule module =
                new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(Embedding.class, new EmbeddingSerializer());
        module.addDeserializer(Embedding.class, new EmbeddingDeserializer());
        objectMapper.registerModule(module);
    }

    private static Map<String, Object> createTestMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "test");
        metadata.put("category", "unit-test");
        metadata.put("timestamp", Instant.now().toEpochMilli());
        metadata.put("important", true);
        metadata.put("score", 0.95);
        metadata.put("tags", Arrays.asList("test", "metadata", "redis"));
        return metadata;
    }

    @Test
    void should_store_and_retrieve_embedding_with_metadata() throws JsonProcessingException {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);
        CacheEntry entry = new CacheEntry(TEST_TEXT, TEST_EMBEDDING, TEST_METADATA);
        String serializedEntry = objectMapper.writeValueAsString(entry);

        // Mock Redis JSON operations
        when(jedis.jsonGet(key)).thenReturn(serializedEntry);

        // When
        cache.put(TEST_TEXT, TEST_EMBEDDING, TEST_METADATA);
        Optional<Map.Entry<Embedding, Map<String, Object>>> result = cache.getWithMetadata(TEST_TEXT);

        // Then
        verify(jedis).jsonSet(eq(key), eq(ROOT_PATH), anyString());
        assertThat(result).isPresent();
        assertThat(result.get().getKey().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.get().getValue())
                .containsEntry("source", "test")
                .containsEntry("category", "unit-test")
                .containsEntry("important", true)
                .containsEntry("score", 0.95)
                .containsKey("timestamp")
                .containsKey("tags");

        // Verify tags list
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) result.get().getValue().get("tags");
        assertThat(tags).containsExactly("test", "metadata", "redis");
    }

    @Test
    void should_store_and_retrieve_embedding_with_custom_ttl() throws JsonProcessingException {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);
        CacheEntry entry = new CacheEntry(TEST_TEXT, TEST_EMBEDDING, TEST_METADATA);
        String serializedEntry = objectMapper.writeValueAsString(entry);
        long customTtl = 7200; // 2 hours

        when(jedis.jsonGet(key)).thenReturn(serializedEntry);

        // When
        cache.put(TEST_TEXT, TEST_EMBEDDING, TEST_METADATA, customTtl);
        Optional<Map.Entry<Embedding, Map<String, Object>>> result = cache.getWithMetadata(TEST_TEXT);

        // Then
        verify(jedis).jsonSet(eq(key), eq(ROOT_PATH), anyString());
        verify(jedis).expire(eq(key), eq(customTtl));
        assertThat(result).isPresent();
    }

    @Test
    void should_support_batch_operations_with_metadata() throws JsonProcessingException {
        // Given
        String text1 = "Hello, world!";
        String text2 = "Goodbye, world!";

        Embedding embedding1 = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
        Embedding embedding2 = new Embedding(new float[] {0.4f, 0.5f, 0.6f});

        Map<String, Object> metadata1 = new HashMap<>(TEST_METADATA);
        Map<String, Object> metadata2 = new HashMap<>(TEST_METADATA);
        metadata2.put("category", "farewell");

        String key1 = TEST_PREFIX + "embedding:" + cache.md5(text1);
        String key2 = TEST_PREFIX + "embedding:" + cache.md5(text2);

        CacheEntry entry1 = new CacheEntry(text1, embedding1, metadata1);
        CacheEntry entry2 = new CacheEntry(text2, embedding2, metadata2);

        String serializedEntry1 = objectMapper.writeValueAsString(entry1);
        String serializedEntry2 = objectMapper.writeValueAsString(entry2);

        // Mock Redis pipeline operations
        Pipeline pipeline = mock(Pipeline.class);
        when(jedis.pipelined()).thenReturn(pipeline);

        // For mget with JSON
        redis.clients.jedis.Response<Object> jsonResp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Object> jsonResp2 = mock(redis.clients.jedis.Response.class);
        // For legacy format fallback
        redis.clients.jedis.Response<String> legacyResp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<String> legacyResp2 = mock(redis.clients.jedis.Response.class);

        when(pipeline.jsonGet(key1)).thenReturn(jsonResp1);
        when(pipeline.jsonGet(key2)).thenReturn(jsonResp2);
        when(pipeline.get(key1)).thenReturn(legacyResp1);
        when(pipeline.get(key2)).thenReturn(legacyResp2);

        when(jsonResp1.get()).thenReturn(serializedEntry1);
        when(jsonResp2.get()).thenReturn(serializedEntry2);
        when(legacyResp1.get()).thenReturn(null);
        when(legacyResp2.get()).thenReturn(null);

        // When: Put with metadata
        Map<String, Map.Entry<Embedding, Map<String, Object>>> embeddings = new HashMap<>();
        embeddings.put(text1, Map.entry(embedding1, metadata1));
        embeddings.put(text2, Map.entry(embedding2, metadata2));

        cache.mputWithMetadata(embeddings);

        // Then: Verify mput
        verify(pipeline).jsonSet(eq(key1), eq(ROOT_PATH), anyString());
        verify(pipeline).jsonSet(eq(key2), eq(ROOT_PATH), anyString());
        verify(pipeline).sync();

        // When: Get with metadata
        Map<String, Map.Entry<Embedding, Map<String, Object>>> results =
                cache.mgetWithMetadata(Arrays.asList(text1, text2));

        // Then: Verify mget results
        assertThat(results).hasSize(2);
        assertThat(results).containsKeys(text1, text2);

        Map.Entry<Embedding, Map<String, Object>> result1 = results.get(text1);
        assertThat(result1.getKey().vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result1.getValue()).containsEntry("category", "unit-test");

        Map.Entry<Embedding, Map<String, Object>> result2 = results.get(text2);
        assertThat(result2.getKey().vector()).containsExactly(0.4f, 0.5f, 0.6f);
        assertThat(result2.getValue()).containsEntry("category", "farewell");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_find_by_metadata() throws JsonProcessingException {
        // Given
        String text1 = "Finance news";
        String text2 = "Sports news";
        String text3 = "Politics news";

        Embedding embedding1 = new Embedding(new float[] {0.1f, 0.2f, 0.3f});
        Embedding embedding2 = new Embedding(new float[] {0.4f, 0.5f, 0.6f});
        Embedding embedding3 = new Embedding(new float[] {0.7f, 0.8f, 0.9f});

        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("category", "finance");
        metadata1.put("importance", 5);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("category", "sports");
        metadata2.put("importance", 3);

        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("category", "politics");
        metadata3.put("importance", 5);

        CacheEntry entry1 = new CacheEntry(text1, embedding1, metadata1);
        CacheEntry entry2 = new CacheEntry(text2, embedding2, metadata2);
        CacheEntry entry3 = new CacheEntry(text3, embedding3, metadata3);

        String serializedEntry1 = objectMapper.writeValueAsString(entry1);
        String serializedEntry2 = objectMapper.writeValueAsString(entry2);
        String serializedEntry3 = objectMapper.writeValueAsString(entry3);

        // Mock Redis scan and get operations
        ScanResult<String> scanResult = mock(ScanResult.class);
        when(scanResult.getResult())
                .thenReturn(Arrays.asList(
                        TEST_PREFIX + "embedding:key1",
                        TEST_PREFIX + "embedding:key2",
                        TEST_PREFIX + "embedding:key3"));
        when(scanResult.getCursor()).thenReturn("0");
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(scanResult);

        // Mock pipeline for batch get with JSON
        Pipeline pipeline = mock(Pipeline.class);
        redis.clients.jedis.Response<Object> resp1 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Object> resp2 = mock(redis.clients.jedis.Response.class);
        redis.clients.jedis.Response<Object> resp3 = mock(redis.clients.jedis.Response.class);

        when(jedis.pipelined()).thenReturn(pipeline);
        when(pipeline.jsonGet(TEST_PREFIX + "embedding:key1")).thenReturn(resp1);
        when(pipeline.jsonGet(TEST_PREFIX + "embedding:key2")).thenReturn(resp2);
        when(pipeline.jsonGet(TEST_PREFIX + "embedding:key3")).thenReturn(resp3);

        when(resp1.get()).thenReturn(serializedEntry1);
        when(resp2.get()).thenReturn(serializedEntry2);
        when(resp3.get()).thenReturn(serializedEntry3);

        // When: Find by importance = 5
        Map<String, Object> filter = new HashMap<>();
        filter.put("importance", 5);

        Map<String, Map.Entry<Embedding, Map<String, Object>>> results = cache.findByMetadata(filter, 10);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).containsKeys(text1, text3);

        // When: Find by specific category
        filter = new HashMap<>();
        filter.put("category", "finance");

        results = cache.findByMetadata(filter, 10);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results).containsKey(text1);

        // Verify the metadata is preserved
        Map.Entry<Embedding, Map<String, Object>> financeResult = results.get(text1);
        assertThat(financeResult.getValue())
                .containsEntry("category", "finance")
                .containsEntry("importance", 5);
    }

    @Test
    void should_handle_legacy_format_for_backward_compatibility() {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);
        // Legacy format is comma-separated values of the vector
        String legacyValue = "0.1,0.2,0.3";

        // No JSON value found
        when(jedis.jsonGet(key)).thenReturn(null);
        // But legacy format is available
        when(jedis.get(key)).thenReturn(legacyValue);

        // When
        Optional<Embedding> result = cache.get(TEST_TEXT);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().vector()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void should_track_access_statistics() throws JsonProcessingException {
        // Given
        String key = TEST_PREFIX + "embedding:" + cache.md5(TEST_TEXT);
        CacheEntry entry = new CacheEntry(TEST_TEXT, TEST_EMBEDDING, TEST_METADATA);

        // Create a CacheEntry that has access count 9 (so next access will be a multiple of 10)
        CacheEntry entryWithCount9 =
                new CacheEntry(TEST_TEXT, TEST_EMBEDDING, TEST_METADATA, null, Instant.now(), Instant.now(), 9);
        String jsonWithCount9 = objectMapper.writeValueAsString(entryWithCount9);

        // Mock the Redis JSON methods
        when(jedis.jsonGet(key)).thenReturn(jsonWithCount9);
        when(jedis.jsonSet(eq(key), eq(ROOT_PATH), anyString())).thenReturn("OK");

        // When: Access the entry once, which should trigger an update since count will be 10
        cache.getWithMetadata(TEST_TEXT);

        // Then: Verify the entry was updated because access count reached a multiple of 10
        verify(jedis).jsonSet(eq(key), eq(ROOT_PATH), anyString());
    }

    // Tests for legacy format and access statistics are implemented above
}
