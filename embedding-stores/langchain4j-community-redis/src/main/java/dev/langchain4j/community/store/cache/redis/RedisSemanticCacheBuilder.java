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
     * Builds a new RedisSemanticCache with the configured options.
     *
     * @return A new RedisSemanticCache instance
     */
    public RedisSemanticCache build() {
        if (redis == null) {
            throw new IllegalStateException("Redis client is required");
        }
        if (embeddingModel == null) {
            throw new IllegalStateException("Embedding model is required");
        }

        return new RedisSemanticCache(redis, embeddingModel, ttl, prefix, similarityThreshold, enableEnhancedFilters);
    }
}
