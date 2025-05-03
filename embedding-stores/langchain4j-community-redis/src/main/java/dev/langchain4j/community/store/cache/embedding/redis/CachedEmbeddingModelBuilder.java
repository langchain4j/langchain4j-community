package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.model.embedding.EmbeddingModel;
import redis.clients.jedis.JedisPooled;

/**
 * Builder for {@link CachedEmbeddingModel} to simplify configuration and creation.
 */
public class CachedEmbeddingModelBuilder {

    private EmbeddingModel delegate;
    private EmbeddingCache cache;
    private String host = "localhost";
    private int port = 6379;
    private String user = null;
    private String password = null;
    private String keyPrefix = "langchain4j:";
    private int maxCacheSize = 1000;
    private long ttlSeconds = 86400; // 24 hours
    private JedisPooled redis;

    /**
     * Creates a new builder instance.
     *
     * @return A new CachedEmbeddingModelBuilder
     */
    public static CachedEmbeddingModelBuilder builder() {
        return new CachedEmbeddingModelBuilder();
    }

    /**
     * Sets the delegate embedding model.
     *
     * @param delegate The embedding model to delegate to when cache misses occur
     * @return This builder
     */
    public CachedEmbeddingModelBuilder delegate(EmbeddingModel delegate) {
        this.delegate = delegate;
        return this;
    }

    /**
     * Sets a pre-configured embedding cache.
     * <p>If a cache is provided, other Redis configuration settings are ignored.</p>
     *
     * @param cache The embedding cache
     * @return This builder
     */
    public CachedEmbeddingModelBuilder cache(EmbeddingCache cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Sets the Redis client to use.
     * <p>If a client is provided, host, port, user, and password settings are ignored.</p>
     *
     * @param redis The Redis client
     * @return This builder
     */
    public CachedEmbeddingModelBuilder redis(JedisPooled redis) {
        this.redis = redis;
        return this;
    }

    /**
     * Sets the Redis host.
     * <p>Default is "localhost".</p>
     *
     * @param host The Redis host
     * @return This builder
     */
    public CachedEmbeddingModelBuilder host(String host) {
        this.host = host;
        return this;
    }

    /**
     * Sets the Redis port.
     * <p>Default is 6379.</p>
     *
     * @param port The Redis port
     * @return This builder
     */
    public CachedEmbeddingModelBuilder port(int port) {
        this.port = port;
        return this;
    }

    /**
     * Sets the Redis user.
     * <p>Default is null (no user).</p>
     *
     * @param user The Redis user
     * @return This builder
     */
    public CachedEmbeddingModelBuilder user(String user) {
        this.user = user;
        return this;
    }

    /**
     * Sets the Redis password.
     * <p>Default is null (no password).</p>
     *
     * @param password The Redis password
     * @return This builder
     */
    public CachedEmbeddingModelBuilder password(String password) {
        this.password = password;
        return this;
    }

    /**
     * Sets the key prefix for Redis keys.
     * <p>Default is "langchain4j:".</p>
     *
     * @param keyPrefix The key prefix
     * @return This builder
     */
    public CachedEmbeddingModelBuilder keyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
        return this;
    }

    /**
     * Sets the maximum cache size.
     * <p>Default is 1000 entries. Set to 0 for unlimited size.</p>
     *
     * @param maxCacheSize The maximum number of entries in the cache
     * @return This builder
     */
    public CachedEmbeddingModelBuilder maxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        return this;
    }

    /**
     * Sets the TTL (time-to-live) for cache entries in seconds.
     * <p>Default is 86400 (24 hours). Set to 0 for no expiration.</p>
     *
     * @param ttlSeconds TTL in seconds
     * @return This builder
     */
    public CachedEmbeddingModelBuilder ttlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        return this;
    }

    /**
     * Builds a new {@link CachedEmbeddingModel} with the configured parameters.
     *
     * @return A new CachedEmbeddingModel instance
     * @throws IllegalStateException if delegate is not specified
     */
    public CachedEmbeddingModel build() {
        if (delegate == null) {
            throw new IllegalStateException("Delegate embedding model must be specified");
        }

        EmbeddingCache embeddingCache = cache;

        // Create a cache if one wasn't provided
        if (embeddingCache == null) {
            RedisEmbeddingCacheBuilder cacheBuilder = RedisEmbeddingCacheBuilder.builder()
                    .keyPrefix(keyPrefix)
                    .maxCacheSize(maxCacheSize)
                    .ttlSeconds(ttlSeconds);

            if (redis != null) {
                cacheBuilder.redis(redis);
            } else {
                cacheBuilder.host(host).port(port).user(user).password(password);
            }

            embeddingCache = cacheBuilder.build();
        }

        return new CachedEmbeddingModel(delegate, embeddingCache);
    }
}
