package dev.langchain4j.community.store.cache.embedding.redis;

import redis.clients.jedis.JedisPooled;

/**
 * Builder for {@link RedisEmbeddingCache} to simplify configuration and creation.
 */
public class RedisEmbeddingCacheBuilder {

    private JedisPooled redis;
    private String host = "localhost";
    private int port = 6379;
    private String user = null;
    private String password = null;
    private String keyPrefix = "langchain4j:";
    private int maxCacheSize = 1000; // Default max size
    private long ttlSeconds = 86400; // Default to 24 hours

    /**
     * Creates a new builder instance.
     *
     * @return A new RedisEmbeddingCacheBuilder
     */
    public static RedisEmbeddingCacheBuilder builder() {
        return new RedisEmbeddingCacheBuilder();
    }

    /**
     * Sets the Redis client to use.
     * <p>If a client is provided, host, port, and password settings are ignored.</p>
     *
     * @param redis The Redis client
     * @return This builder
     */
    public RedisEmbeddingCacheBuilder redis(JedisPooled redis) {
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
    public RedisEmbeddingCacheBuilder host(String host) {
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
    public RedisEmbeddingCacheBuilder port(int port) {
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
    public RedisEmbeddingCacheBuilder user(String user) {
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
    public RedisEmbeddingCacheBuilder password(String password) {
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
    public RedisEmbeddingCacheBuilder keyPrefix(String keyPrefix) {
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
    public RedisEmbeddingCacheBuilder maxCacheSize(int maxCacheSize) {
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
    public RedisEmbeddingCacheBuilder ttlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
        return this;
    }

    /**
     * Builds a new {@link RedisEmbeddingCache} with the configured parameters.
     *
     * @return A new RedisEmbeddingCache instance
     */
    public RedisEmbeddingCache build() {
        JedisPooled redisClient = redis;

        // Create a new client if one wasn't provided
        if (redisClient == null) {
            if (user != null && !user.isEmpty()) {
                redisClient = new JedisPooled(host, port, user, password);
            } else if (password != null && !password.isEmpty()) {
                redisClient = new JedisPooled(host, port, null, password);
            } else {
                redisClient = new JedisPooled(host, port);
            }
        }

        return new RedisEmbeddingCache(redisClient, keyPrefix, maxCacheSize, ttlSeconds);
    }
}
