package dev.langchain4j.community.store.cache.redis;

import dev.langchain4j.model.embedding.EmbeddingModel;
import redis.clients.jedis.JedisPooled;

/**
 * Builder for the RedisSemanticCache.
 */
public class RedisSemanticCacheBuilder {
    private JedisPooled redis;
    private EmbeddingModel embeddingModel;
    private Integer ttl;
    private String prefix = "semantic-cache";
    private Float similarityThreshold = 0.2f;
    private boolean enableEnhancedFilters = false;
    private String huggingFaceAccessToken = null;
    private boolean useDefaultRedisModel = false;

    /**
     * Creates a new RedisSemanticCacheBuilder instance.
     */
    public RedisSemanticCacheBuilder() {}

    /**
     * Sets the Redis client to use.
     *
     * @param redis The Redis client
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder redis(JedisPooled redis) {
        this.redis = redis;
        return this;
    }

    /**
     * Sets the embedding model to use for vectorizing prompts.
     *
     * @param embeddingModel The embedding model
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder embeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    /**
     * Sets the time-to-live for cache entries in seconds.
     *
     * @param ttl Time-to-live in seconds, or null for no expiration
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder ttl(Integer ttl) {
        this.ttl = ttl;
        return this;
    }

    /**
     * Sets the prefix for all keys stored in Redis.
     *
     * @param prefix The key prefix
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * Sets the threshold for semantic similarity.
     *
     * @param similarityThreshold The similarity threshold (0.0 to 1.0)
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder similarityThreshold(Float similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
        return this;
    }

    /**
     * Enables enhanced filter support for the semantic cache.
     * This allows the use of the Redis-specific filter expressions.
     *
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder enableEnhancedFilters() {
        this.enableEnhancedFilters = true;
        return this;
    }

    /**
     * Sets the access token for the HuggingFace API, used when creating the default
     * Redis LangCache embedding model. This is only used if no explicit embedding model
     * is provided via {@link #embeddingModel(EmbeddingModel)}.
     *
     * @param accessToken The HuggingFace API access token
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder huggingFaceAccessToken(String accessToken) {
        this.huggingFaceAccessToken = accessToken;
        return this;
    }

    /**
     * Use the Redis LangCache embedding model (redis/langcache-embed-v1) by default.
     * <p>
     * This model is specifically fine-tuned for semantic caching and provides better performance
     * for caching LLM responses. For more information about this model, see:
     * <a href="https://huggingface.co/redis/langcache-embed-v1">https://huggingface.co/redis/langcache-embed-v1</a>
     * and <a href="https://arxiv.org/abs/2504.02268">https://arxiv.org/abs/2504.02268</a>.
     * </p>
     * <p>
     * To use this model, you must have the langchain4j-hugging-face dependency on your classpath.
     * </p>
     *
     * @return This builder for chaining
     */
    public RedisSemanticCacheBuilder useDefaultRedisModel() {
        this.useDefaultRedisModel = true;
        return this;
    }

    /**
     * Builds a new RedisSemanticCache with the configured options.
     *
     * @return A new RedisSemanticCache instance
     */
    public RedisSemanticCache build() {
        if (redis == null) {
            throw new IllegalStateException("Redis client is required");
        }

        // If no embedding model is explicitly provided, but useDefaultRedisModel is enabled,
        // try to create the Redis LangCache embedding model
        if (embeddingModel == null && useDefaultRedisModel) {
            try {
                // Import class dynamically to avoid direct dependency
                Class<?> factoryClass = Class.forName(
                        "dev.langchain4j.community.store.embedding.redis.RedisLangCacheEmbeddingModelFactory");

                // Use reflection to call the create method
                java.lang.reflect.Method createMethod = factoryClass.getMethod("create", String.class);
                embeddingModel = (EmbeddingModel) createMethod.invoke(null, huggingFaceAccessToken);

                if (embeddingModel != null) {
                    System.out.println("[INFO] Using Redis LangCache embedding model (redis/langcache-embed-v1)");
                    System.out.println("[INFO] This model is specifically fine-tuned for semantic caching.");
                    System.out.println("[INFO] For more information: https://huggingface.co/redis/langcache-embed-v1");
                }
            } catch (Exception e) {
                System.out.println("[WARN] Could not create Redis LangCache embedding model: " + e.getMessage());
                System.out.println("[WARN] Make sure langchain4j-hugging-face is on the classpath");
            }
        }

        // Still require an embedding model one way or another
        if (embeddingModel == null) {
            throw new IllegalStateException(
                    "Embedding model is required. Either provide one explicitly or enable useDefaultRedisModel()");
        }

        return new RedisSemanticCache(redis, embeddingModel, ttl, prefix, similarityThreshold, enableEnhancedFilters);
    }
}
