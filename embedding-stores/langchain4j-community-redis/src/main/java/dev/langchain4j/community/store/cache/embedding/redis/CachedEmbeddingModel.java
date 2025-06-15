package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.community.store.cache.EmbeddingCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

/**
 * A wrapper for EmbeddingModel that caches embedding results in Redis.
 * <p>
 * This model intercepts embedding requests, checks the cache first, and only
 * calls the underlying model when necessary, caching the results for future use.
 */
public class CachedEmbeddingModel implements EmbeddingModel {

    private static final Logger logger = LoggerFactory.getLogger(CachedEmbeddingModel.class);

    private final EmbeddingModel delegate;
    private final EmbeddingCache cache;

    /**
     * Creates a new cached embedding model.
     *
     * @param delegate The underlying embedding model to delegate to when cache misses occur
     * @param cache    The cache to use for storing and retrieving embeddings
     */
    public CachedEmbeddingModel(EmbeddingModel delegate, EmbeddingCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    /**
     * Embeds a single text segment, using the cache if available.
     *
     * @param textSegment The text segment to embed
     * @return The embedding response
     */
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        String text = textSegment.text();

        // Check cache first
        Optional<Embedding> cachedEmbedding = cache.get(text);

        if (cachedEmbedding.isPresent()) {
            logger.debug("Cache hit for text: {}", text);
            return Response.from(cachedEmbedding.get());
        }

        logger.debug("Cache miss for text: {}", text);
        // Cache miss, get from delegate and store in cache
        Response<Embedding> delegateResponse = delegate.embed(textSegment);
        Embedding embedding = delegateResponse.content();

        // Store in cache for future use
        cache.put(text, embedding);

        return delegateResponse;
    }

    /**
     * Implements the EmbeddingModel interface method to embed a string.
     *
     * @param text The text to embed
     * @return A Response containing the embedding
     */
    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>(textSegments.size());
        List<Integer> cacheMissIndices = new ArrayList<>();
        List<TextSegment> cacheMissSegments = new ArrayList<>();

        // Check cache for each segment
        for (int i = 0; i < textSegments.size(); i++) {
            TextSegment segment = textSegments.get(i);
            String text = segment.text();

            Optional<Embedding> cachedEmbedding = cache.get(text);
            if (cachedEmbedding.isPresent()) {
                logger.debug("Cache hit for text segment {}: {}", i, text);
                embeddings.add(cachedEmbedding.get());
            } else {
                logger.debug("Cache miss for text segment {}: {}", i, text);
                // Add placeholder to maintain order
                embeddings.add(null);
                cacheMissIndices.add(i);
                cacheMissSegments.add(segment);
            }
        }

        // If we had any cache misses, delegate to the underlying model
        if (!cacheMissSegments.isEmpty()) {
            Response<List<Embedding>> delegateResponse = delegate.embedAll(cacheMissSegments);
            List<Embedding> delegateEmbeddings = delegateResponse.content();

            // Update cache and fill in the embeddings
            for (int i = 0; i < cacheMissIndices.size(); i++) {
                int originalIndex = cacheMissIndices.get(i);
                Embedding embedding = delegateEmbeddings.get(i);
                String text = textSegments.get(originalIndex).text();

                // Store in cache
                cache.put(text, embedding);

                // Update our result list
                embeddings.set(originalIndex, embedding);
            }

            // Return with token usage from the delegate
            return Response.from(embeddings, delegateResponse.tokenUsage());
        }

        // If all were cache hits, just return the embeddings with no token usage info
        return Response.from(embeddings);
    }

    /**
     * Clears all entries from the embedding cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Gets the cache instance used by this model.
     *
     * @return The EmbeddingCache instance
     */
    public EmbeddingCache getCache() {
        return cache;
    }

    /**
     * Gets the underlying delegate embedding model.
     *
     * @return The underlying embedding model
     */
    public EmbeddingModel getDelegate() {
        return delegate;
    }

    /**
     * Creates a new builder instance.
     *
     * @return A new CachedEmbeddingModelBuilder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CachedEmbeddingModel} to simplify configuration and creation.
     */
    public static class Builder {

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
         * Sets the delegate embedding model.
         *
         * @param delegate The embedding model to delegate to when cache misses occur
         * @return This builder
         */
        public Builder delegate(EmbeddingModel delegate) {
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
        public Builder cache(EmbeddingCache cache) {
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
        public Builder redis(JedisPooled redis) {
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
        public Builder host(String host) {
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
        public Builder port(int port) {
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
        public Builder user(String user) {
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
        public Builder password(String password) {
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
        public Builder keyPrefix(String keyPrefix) {
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
        public Builder maxCacheSize(int maxCacheSize) {
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
        public Builder ttlSeconds(long ttlSeconds) {
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
                RedisEmbeddingCache.Builder cacheBuilder = RedisEmbeddingCache.builder()
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
}
