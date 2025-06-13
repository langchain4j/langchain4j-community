package dev.langchain4j.community.store.routing.redis.spring;

import static dev.langchain4j.community.store.routing.redis.spring.RedisSemanticRouterProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreProperties;
import dev.langchain4j.community.store.routing.redis.RedisSemanticRouter;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.JedisPooled;

/**
 * Auto-configuration for Redis Semantic Router.
 *
 * <p>This auto-configuration provides a {@link RedisSemanticRouter} bean
 * when the property {@code langchain4j.community.redis.semantic-router.enabled}
 * is set to {@code true}.</p>
 *
 * <p>An {@link EmbeddingModel} bean must be available in the application context
 * for this auto-configuration to work.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties({RedisSemanticRouterProperties.class, RedisEmbeddingStoreProperties.class})
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true")
@ConditionalOnBean(EmbeddingModel.class)
public class RedisSemanticRouterAutoConfiguration {

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
    public RedisSemanticRouter redisSemanticRouter(
            RedisSemanticRouterProperties properties, JedisPooled jedisPooled, EmbeddingModel embeddingModel) {

        return RedisSemanticRouter.builder()
                .redis(jedisPooled)
                .embeddingModel(embeddingModel)
                .prefix(properties.getPrefix())
                .maxResults(properties.getMaxResults())
                .build();
    }
}
