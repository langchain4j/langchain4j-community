package dev.langchain4j.community.store.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import redis.clients.jedis.JedisPooled;

/**
 * Unit tests for RedisSemanticCache.
 *
 * These tests are designed to verify the semantic caching functionality,
 * following the pattern from the Python langchain-redis implementation.
 */
public class RedisSemanticCacheTest {

    @Mock
    private JedisPooled jedisPooled;

    @Mock
    private EmbeddingModel embeddingModel;

    private static final float[] TEST_EMBEDDING_VECTOR_1 = {0.1f, 0.2f, 0.3f};
    private static final float[] TEST_EMBEDDING_VECTOR_2 = {0.4f, 0.5f, 0.6f};

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup the embedding model mock
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            Embedding embedding = text.contains("similar")
                    ? Embedding.from(TEST_EMBEDDING_VECTOR_1)
                    : Embedding.from(TEST_EMBEDDING_VECTOR_2);
            return new Response<>(embedding, null, null);
        });

        // Mock Redis operations to avoid API conflicts in tests
        doReturn(java.util.Set.of("semantic-cache-index")).when(jedisPooled).ftList();
    }

    @Test
    public void test_construction() {
        // Just test that we can create the semantic cache object without exceptions
        RedisSemanticCache redisSemanticCache =
                new RedisSemanticCache(jedisPooled, embeddingModel, 3600, "semantic-cache", 0.2f);

        // Simple verification that the object is constructed correctly
        assertThat(redisSemanticCache).isNotNull();
    }
}
