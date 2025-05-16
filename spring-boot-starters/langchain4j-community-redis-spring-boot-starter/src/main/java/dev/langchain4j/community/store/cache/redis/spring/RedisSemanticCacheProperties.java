package dev.langchain4j.community.store.cache.redis.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Redis Semantic Cache.
 */
@ConfigurationProperties(prefix = RedisSemanticCacheProperties.CONFIG_PREFIX)
public class RedisSemanticCacheProperties {

    public static final String CONFIG_PREFIX = "langchain4j.community.redis.semantic-cache";

    /**
     * Whether to enable the Redis semantic cache.
     */
    private boolean enabled = false;

    /**
     * Time-to-live in seconds for cache entries.
     */
    private Integer ttl;

    /**
     * Prefix for all cache keys stored in Redis.
     */
    private String prefix = "semantic-cache";

    /**
     * Threshold for semantic similarity matching.
     * Lower values require closer matches (more strict).
     * Higher values allow more distant matches (more lenient).
     */
    private Float similarityThreshold = 0.2f;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Float getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Float similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
