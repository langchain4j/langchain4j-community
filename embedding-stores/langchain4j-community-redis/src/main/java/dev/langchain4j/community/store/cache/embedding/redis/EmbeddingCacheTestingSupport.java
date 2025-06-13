package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * Utility class for configuring embedding models for testing.
 * This provides easy ways to set up recording and playback of embeddings.
 */
public class EmbeddingCacheTestingSupport {

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with static methods only.
     */
    private EmbeddingCacheTestingSupport() {
        // Utility class, no instances needed
    }

    /**
     * Creates a model that records all embeddings it generates.
     * The recordings are stored in a test-specific namespace in the cache.
     *
     * @param model The embedding model to wrap
     * @param testContextId A unique identifier for this test context
     * @return A new model that records all embeddings
     */
    public static EmbeddingModel recordMode(EmbeddingModel model, String testContextId) {
        if (model instanceof CachedEmbeddingModel) {
            CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) model;
            EmbeddingCache originalCache = cachedModel.getCache();
            TestingEmbeddingCache testCache = TestingEmbeddingCache.inRecordMode(originalCache, testContextId);

            return CachedEmbeddingModelBuilder.builder()
                    .delegate(cachedModel.getDelegate())
                    .cache(testCache)
                    .build();
        }

        // If not already cached, set up with a Redis cache in record mode
        RedisEmbeddingCache cache = RedisEmbeddingCacheBuilder.builder()
                .keyPrefix("test-recordings:")
                .build();

        TestingEmbeddingCache testCache = TestingEmbeddingCache.inRecordMode(cache, testContextId);

        return CachedEmbeddingModelBuilder.builder()
                .delegate(model)
                .cache(testCache)
                .build();
    }

    /**
     * Creates a model that uses previously recorded embeddings.
     * This allows tests to run without making real API calls.
     *
     * @param model The embedding model to wrap
     * @param testContextId A unique identifier for this test context
     * @return A new model that uses previously recorded embeddings
     */
    public static EmbeddingModel playMode(EmbeddingModel model, String testContextId) {
        if (model instanceof CachedEmbeddingModel) {
            CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) model;
            EmbeddingCache originalCache = cachedModel.getCache();
            TestingEmbeddingCache testCache = TestingEmbeddingCache.inPlayMode(originalCache, testContextId);

            return CachedEmbeddingModelBuilder.builder()
                    .delegate(cachedModel.getDelegate())
                    .cache(testCache)
                    .build();
        }

        // If not already cached, set up with a Redis cache in play mode
        RedisEmbeddingCache cache = RedisEmbeddingCacheBuilder.builder()
                .keyPrefix("test-recordings:")
                .build();

        TestingEmbeddingCache testCache = TestingEmbeddingCache.inPlayMode(cache, testContextId);

        return CachedEmbeddingModelBuilder.builder()
                .delegate(model)
                .cache(testCache)
                .build();
    }

    /**
     * Configures a global cache for testing in record mode.
     * All models wrapped with EmbeddingModelCache.wrap() will use this cache.
     *
     * @param host Redis host
     * @param port Redis port
     * @param testContextId A unique identifier for this test context
     */
    public static void configureGlobalRecordMode(String host, int port, String testContextId) {
        RedisEmbeddingCache cache =
                RedisEmbeddingCacheBuilder.builder().host(host).port(port).build();

        TestingEmbeddingCache testCache = TestingEmbeddingCache.inRecordMode(cache, testContextId);
        EmbeddingModelCache.configureGlobalCache(testCache);
    }

    /**
     * Configures a global cache for testing in play mode.
     * All models wrapped with EmbeddingModelCache.wrap() will use this cache.
     *
     * @param host Redis host
     * @param port Redis port
     * @param testContextId A unique identifier for this test context
     */
    public static void configureGlobalPlayMode(String host, int port, String testContextId) {
        RedisEmbeddingCache cache =
                RedisEmbeddingCacheBuilder.builder().host(host).port(port).build();

        TestingEmbeddingCache testCache = TestingEmbeddingCache.inPlayMode(cache, testContextId);
        EmbeddingModelCache.configureGlobalCache(testCache);
    }
}
