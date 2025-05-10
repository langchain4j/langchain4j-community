package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

class RedisEmbeddingCacheBuilderTest {

    @Test
    void should_build_with_provided_redis_client() {
        // Given
        JedisPooled redis = mock(JedisPooled.class);

        // When
        RedisEmbeddingCache cache = RedisEmbeddingCacheBuilder.builder()
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
        RedisEmbeddingCache cache = RedisEmbeddingCacheBuilder.builder()
                .host("localhost")
                .port(6379)
                .keyPrefix("test:")
                .build();

        // Then
        assertThat(cache).isNotNull();
        assertThat(cache).hasFieldOrPropertyWithValue("keyPrefix", "test:");
    }
}
