package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.community.store.cache.EmbeddingCache;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

class CachedEmbeddingModelBuilderTest {

    @Test
    void should_build_with_delegate_and_custom_cache() {
        // Given
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        EmbeddingCache cache = mock(EmbeddingCache.class);

        // When
        CachedEmbeddingModel model =
                CachedEmbeddingModel.builder().delegate(delegate).cache(cache).build();

        // Then
        assertThat(model).isNotNull();
        assertThat(model).hasFieldOrPropertyWithValue("delegate", delegate);
        assertThat(model).hasFieldOrPropertyWithValue("cache", cache);
    }

    @Test
    void should_build_with_delegate_and_auto_created_cache() {
        // Given
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        JedisPooled redis = mock(JedisPooled.class);

        // When
        CachedEmbeddingModel model = CachedEmbeddingModel.builder()
                .delegate(delegate)
                .redis(redis)
                .keyPrefix("test:")
                .maxCacheSize(500)
                .ttlSeconds(3600)
                .build();

        // Then
        assertThat(model).isNotNull();
        assertThat(model).hasFieldOrPropertyWithValue("delegate", delegate);
        assertThat(model.getCache()).isInstanceOf(RedisEmbeddingCache.class);
    }

    @Test
    void should_throw_exception_when_delegate_not_specified() {
        // Given
        EmbeddingCache cache = mock(EmbeddingCache.class);

        // When/Then
        assertThatThrownBy(() -> CachedEmbeddingModel.builder().cache(cache).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delegate embedding model must be specified");
    }
}
