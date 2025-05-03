package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Provides global caching capabilities for embedding models.
 * This utility class allows for configuring caching once and applying it
 * to any embedding model as needed.
 */
public class EmbeddingModelCache {

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private EmbeddingModelCache() {
        // Utility class, no instances needed
    }

    private static EmbeddingCache defaultCache;
    private static boolean globalCachingEnabled = false;

    /**
     * Configure the global cache to use the specified cache implementation.
     *
     * @param cache The cache implementation to use globally
     */
    public static void configureGlobalCache(EmbeddingCache cache) {
        defaultCache = cache;
        globalCachingEnabled = true;
    }

    /**
     * Configure the global cache to use Redis with the specified settings.
     *
     * @param host Redis host
     * @param port Redis port
     */
    public static void configureGlobalRedisCache(String host, int port) {
        defaultCache =
                RedisEmbeddingCacheBuilder.builder().host(host).port(port).build();
        globalCachingEnabled = true;
    }

    /**
     * Disable global caching.
     */
    public static void disableGlobalCache() {
        globalCachingEnabled = false;
    }

    /**
     * Check if global caching is enabled.
     *
     * @return true if global caching is enabled, false otherwise
     */
    public static boolean isGlobalCachingEnabled() {
        return globalCachingEnabled && defaultCache != null;
    }

    /**
     * Wrap the given embedding model with the global cache, if enabled.
     * If global caching is disabled or no cache is configured, returns the original model.
     * If the model is already a cached model, returns it as is.
     *
     * @param model The embedding model to potentially wrap with a cache
     * @return The original model or a wrapped model with caching
     */
    public static EmbeddingModel wrap(EmbeddingModel model) {
        if (!globalCachingEnabled || defaultCache == null) {
            return model; // Pass through if caching not enabled
        }

        // If already wrapped, don't double-wrap
        if (model instanceof CachedEmbeddingModel) {
            return model;
        }

        return CachedEmbeddingModelBuilder.builder()
                .delegate(model)
                .cache(defaultCache)
                .build();
    }
}
