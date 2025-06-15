package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.community.store.cache.EmbeddingCache;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.function.Consumer;

/**
 * Interface for embedding model builders that support caching.
 * This provides a consistent way to add caching to any embedding model builder.
 *
 * @param <T> The concrete builder type, used for method chaining in builder implementations
 */
public interface CacheableEmbeddingModelBuilder<T> {

    /**
     * Enables caching for this embedding model using the provided cache implementation.
     *
     * @param cache The cache implementation to use
     * @return The builder instance for method chaining
     */
    T cacheWith(EmbeddingCache cache);

    /**
     * Enables caching for this embedding model using Redis with default settings.
     *
     * @param host Redis host
     * @param port Redis port
     * @return The builder instance for method chaining
     */
    default T cacheWithRedis(String host, int port) {
        RedisEmbeddingCache cache =
                RedisEmbeddingCache.builder().host(host).port(port).build();
        return cacheWith(cache);
    }

    /**
     * Enables caching for this embedding model using Redis with custom configuration.
     *
     * @param configurator A consumer function that configures the Redis embedding cache builder
     * @return The builder instance for method chaining
     */
    default T cacheWithRedis(Consumer<RedisEmbeddingCache.Builder> configurator) {
        RedisEmbeddingCache.Builder builder = RedisEmbeddingCache.builder();
        configurator.accept(builder);
        return cacheWith(builder.build());
    }

    /**
     * Wraps any embedding model with a cache.
     *
     * @param model The embedding model to cache
     * @param cache The cache implementation to use
     * @return A new embedding model that uses the cache
     */
    static EmbeddingModel withCache(EmbeddingModel model, EmbeddingCache cache) {
        return CachedEmbeddingModel.builder().delegate(model).cache(cache).build();
    }

    /**
     * Wraps any embedding model with a Redis cache using default settings.
     *
     * @param model The embedding model to cache
     * @param host Redis host
     * @param port Redis port
     * @return A new embedding model that uses the Redis cache
     */
    static EmbeddingModel withRedisCache(EmbeddingModel model, String host, int port) {
        // Generate a key prefix based on the model's class name as EmbeddingModel doesn't have a modelId method
        String modelIdentifier = model.getClass().getSimpleName();

        RedisEmbeddingCache cache = RedisEmbeddingCache.builder()
                .host(host)
                .port(port)
                .keyPrefix(modelIdentifier + ":")
                .build();
        return withCache(model, cache);
    }
}
