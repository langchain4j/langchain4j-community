package dev.langchain4j.community.store.cache.redis.spring;

import static dev.langchain4j.community.store.cache.redis.spring.RedisCacheProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.cache.redis.RedisCache;
import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.JedisPooled;

/**
 * Auto-configuration for Redis Cache.
 *
 * <p>This auto-configuration provides a {@link RedisCache} bean
 * when the property {@code langchain4j.community.redis.cache.enabled}
 * is set to {@code true}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties({RedisCacheProperties.class, RedisEmbeddingStoreProperties.class})
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class RedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JedisPooled jedisPooled(RedisEmbeddingStoreProperties properties) {
        if (properties.getUser() != null) {
            return new JedisPooled(
                    properties.getHost(), properties.getPort(), properties.getUser(), properties.getPassword());
        } else {
            return new JedisPooled(properties.getHost(), properties.getPort());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisCache redisCache(RedisCacheProperties properties, JedisPooled jedisPooled) {

        return new RedisCache(jedisPooled, properties.getTtl(), properties.getPrefix());
    }
}
