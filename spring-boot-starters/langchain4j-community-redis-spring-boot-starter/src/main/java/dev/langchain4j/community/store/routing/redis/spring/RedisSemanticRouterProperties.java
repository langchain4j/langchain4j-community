package dev.langchain4j.community.store.routing.redis.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Redis Semantic Router.
 */
@ConfigurationProperties(prefix = RedisSemanticRouterProperties.CONFIG_PREFIX)
public class RedisSemanticRouterProperties {

    public static final String CONFIG_PREFIX = "langchain4j.community.redis.semantic-router";

    /**
     * Whether to enable the Redis semantic router.
     */
    private boolean enabled = false;

    /**
     * Prefix for all router keys stored in Redis.
     */
    private String prefix = "semantic-router";

    /**
     * Maximum number of routes to return when routing.
     */
    private Integer maxResults = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
