package dev.langchain4j.community.store.cache.redis.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Redis Cache.
 */
@ConfigurationProperties(prefix = RedisCacheProperties.CONFIG_PREFIX)
public class RedisCacheProperties {

    public static final String CONFIG_PREFIX = "langchain4j.community.redis.cache";

    /**
     * Whether to enable the Redis cache.
     */
    private boolean enabled = false;

    /**
     * Time-to-live in seconds for cache entries.
     */
    private Integer ttl;

    /**
     * Prefix for all cache keys stored in Redis.
     */
    private String prefix = "redis";

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
}
