package dev.langchain4j.community.store.cache.redis.spring;

import static dev.langchain4j.community.store.cache.redis.spring.RedisSemanticCacheProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.cache.redis.RedisSemanticCache;
import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.JedisPooled;

/**
 * Auto-configuration for Redis Semantic Cache.
 *
 * <p>This auto-configuration provides a {@link RedisSemanticCache} bean
 * when the properties {@code langchain4j.community.redis.semantic-cache.enabled}
 * is set to {@code true}.</p>
 *
 * <p>An {@link EmbeddingModel} bean must be available in the application context
 * for this auto-configuration to work.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties({RedisSemanticCacheProperties.class, RedisEmbeddingStoreProperties.class})
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnBean(EmbeddingModel.class)
public class RedisSemanticCacheAutoConfiguration {

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
    public RedisSemanticCache redisSemanticCache(
            RedisSemanticCacheProperties properties, JedisPooled jedisPooled, EmbeddingModel embeddingModel) {

        return RedisSemanticCache.builder()
                .redis(jedisPooled)
                .embeddingModel(embeddingModel)
                .ttl(properties.getTtl())
                .prefix(properties.getPrefix())
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();
    }
}
