/**
 * Redis-based embedding cache for LangChain4j.
 *
 * <p>This package provides components to cache embeddings in Redis, reducing
 * API costs and latency in applications that frequently compute embeddings for
 * the same text inputs.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link dev.langchain4j.community.store.cache.embedding.redis.EmbeddingCache}:
 *       Interface defining the cache contract</li>
 *   <li>{@link dev.langchain4j.community.store.cache.embedding.redis.RedisEmbeddingCache}:
 *       Redis implementation of the cache</li>
 *   <li>{@link dev.langchain4j.community.store.cache.embedding.redis.CachedEmbeddingModel}:
 *       Wrapper for embedding models that uses the cache</li>
 *   <li>{@link dev.langchain4j.community.store.cache.embedding.redis.RedisEmbeddingCacheBuilder}:
 *       Builder for configuring the Redis cache</li>
 *   <li>{@link dev.langchain4j.community.store.cache.embedding.redis.CachedEmbeddingModelBuilder}:
 *       Builder for configuring the cached model</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * // Create a cached embedding model
 * CachedEmbeddingModel cachedModel = CachedEmbeddingModelBuilder.builder()
 *     .delegate(openAiEmbeddingModel) // Original embedding model
 *     .host("localhost")
 *     .port(6379)
 *     .keyPrefix("my-app:")
 *     .maxCacheSize(1000)
 *     .ttlSeconds(86400) // 24 hours TTL
 *     .build();
 *
 * // Use it in place of the regular embedding model
 * Embedding embedding = cachedModel.embed("Hello, world!");
 * </pre>
 *
 * <p>This cache helps avoid repeated embedding computation for identical
 * texts, conserving API tokens and improving application responsiveness.</p>
 *
 * @since 1.0.0-beta4
 */
package dev.langchain4j.community.store.cache.embedding.redis;
