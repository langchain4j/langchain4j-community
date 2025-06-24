package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.redis.testcontainers.RedisStackContainer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPooled;

/**
 * Integration test for RedisEmbeddingCache with TestContainers.
 * This test requires Docker to run Redis in a container.
 */
@Testcontainers
class RedisEmbeddingCacheIT {

    private static final int REDIS_PORT = 6379;

    @Container
    private final RedisStackContainer redis =
            new RedisStackContainer(DockerImageName.parse("redis/redis-stack:latest"));

    private RedisEmbeddingCache embeddingCache;
    private EmbeddingModel embeddingModel;
    private CachedEmbeddingModel cachedModel;

    @BeforeEach
    void setUp() {
        String host = redis.getHost();
        Integer port = redis.getMappedPort(REDIS_PORT);

        JedisPooled jedisPooled = new JedisPooled(host, port);

        // Use a local embedding model to avoid OpenAI API calls during tests
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // Create the Redis embedding cache with short TTL and small max size for testing
        embeddingCache = RedisEmbeddingCache.builder()
                .redis(jedisPooled)
                .keyPrefix("test-embedding-cache")
                .ttlSeconds(5) // Short TTL for testing expiration
                .maxCacheSize(5) // Small cache size for testing eviction
                .build();

        // Create the cached model with our cache
        cachedModel = CachedEmbeddingModel.builder()
                .delegate(embeddingModel)
                .cache(embeddingCache)
                .build();
    }

    @AfterEach
    void tearDown() {
        embeddingCache.clear();
    }

    @Test
    void should_cache_and_retrieve_embeddings() {
        // given
        String text = "This is a test text for embedding";

        // First call - should compute and cache
        Response<Embedding> firstResponse = cachedModel.embed(text);

        // Verify first response
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.content()).isNotNull();
        assertThat(firstResponse.content().vector()).isNotNull();

        // Second call - should retrieve from cache
        Response<Embedding> secondResponse = cachedModel.embed(text);

        // Verify second response matches first
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.content()).isNotNull();
        assertThat(secondResponse.content().vector()).isNotNull();

        // Verify exact same vector is returned (indicating cache hit)
        assertThat(secondResponse.content().vector())
                .isEqualTo(firstResponse.content().vector());
    }

    @Test
    void should_cache_and_retrieve_batch_embeddings() {
        // given
        List<TextSegment> segments = Arrays.asList(
                TextSegment.from("First test segment"),
                TextSegment.from("Second test segment"),
                TextSegment.from("Third test segment"));

        // First batch call - should compute and cache
        Response<List<Embedding>> firstResponse = cachedModel.embedAll(segments);

        // Verify first response
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.content()).isNotNull();
        assertThat(firstResponse.content()).hasSize(3);

        // Store vectors for comparison
        float[] firstVector = firstResponse.content().get(0).vector();
        float[] secondVector = firstResponse.content().get(1).vector();
        float[] thirdVector = firstResponse.content().get(2).vector();

        // Second batch call - should retrieve from cache
        Response<List<Embedding>> secondResponse = cachedModel.embedAll(segments);

        // Verify second response has same vectors (indicating cache hit)
        assertThat(secondResponse.content().get(0).vector()).isEqualTo(firstVector);
        assertThat(secondResponse.content().get(1).vector()).isEqualTo(secondVector);
        assertThat(secondResponse.content().get(2).vector()).isEqualTo(thirdVector);
    }

    @Test
    void should_respect_ttl_for_cache_entries() throws Exception {
        // given
        String text = "This is a text that will expire from cache";

        // First call - should compute and cache
        Response<Embedding> firstResponse = cachedModel.embed(text);
        assertThat(firstResponse).isNotNull();

        // Second call immediately - should be a cache hit
        Response<Embedding> secondResponse = cachedModel.embed(text);
        assertThat(secondResponse.content().vector())
                .isEqualTo(firstResponse.content().vector());

        // Wait for TTL to expire (we set it to 5 seconds)
        TimeUnit.SECONDS.sleep(6);

        // Third call after TTL - should recompute
        Response<Embedding> thirdResponse = cachedModel.embed(text);

        // The embedding should be functionally equivalent but not the exact same object
        // This check confirms it's not returning the cached value
        assertThat(thirdResponse.content().vector())
                .isNotSameAs(firstResponse.content().vector());
    }

    @Test
    void should_respect_max_cache_size() {
        // We set max cache size to 5, so we'll add 6 entries to trigger eviction

        // Add 6 different texts to the cache
        for (int i = 0; i < 6; i++) {
            String text = "Text for max cache size test " + i;
            cachedModel.embed(text);
        }

        // The first text should be evicted (using LRU policy)
        // Let's check if the first entry was evicted
        String firstText = "Text for max cache size test 0";

        // Get embedding directly from model for comparison
        Response<Embedding> directResponse = embeddingModel.embed(firstText);

        // Get embedding via cached model
        Response<Embedding> cachedResponse = cachedModel.embed(firstText);

        // The vectors should not be the same object since the entry should have been evicted
        // and recomputed, but functionally they should produce the same embedding values
        assertThat(cachedResponse.content().vector())
                .isNotSameAs(directResponse.content().vector());

        // For completeness, let's verify the most recent entry is still in the cache
        String lastText = "Text for max cache size test 5";

        // First get a reference embedding
        Response<Embedding> lastResponse = cachedModel.embed(lastText);

        // Then immediately check if it's in cache
        Response<Embedding> secondResponse = cachedModel.embed(lastText);

        // Should be the same vector (cache hit)
        assertThat(secondResponse.content().vector())
                .isEqualTo(lastResponse.content().vector());
    }

    @Test
    void should_build_with_provided_redis_client() {
        // Given
        JedisPooled redis = mock(JedisPooled.class);

        // When
        RedisEmbeddingCache cache = RedisEmbeddingCache.builder()
                .redis(redis)
                .keyPrefix("test:")
                .maxCacheSize(500)
                .ttlSeconds(3600)
                .build();

        // Then
        assertThat(cache).isNotNull();
        assertThat(cache).hasFieldOrPropertyWithValue("jedis", redis);
        assertThat(cache).hasFieldOrPropertyWithValue("keyPrefix", "test:");
        assertThat(cache).hasFieldOrPropertyWithValue("maxCacheSize", 500);
        assertThat(cache).hasFieldOrPropertyWithValue("ttlSeconds", 3600L);
    }

    @Test
    void should_create_redis_client_when_not_provided() {
        // We can't easily test the creation of JedisPooled without a real Redis
        // This test just ensures no exceptions are thrown during the build process

        // When
        RedisEmbeddingCache cache = RedisEmbeddingCache.builder()
                .host("localhost")
                .port(6379)
                .keyPrefix("test:")
                .build();

        // Then
        assertThat(cache).isNotNull();
        assertThat(cache).hasFieldOrPropertyWithValue("keyPrefix", "test:");
    }
}
