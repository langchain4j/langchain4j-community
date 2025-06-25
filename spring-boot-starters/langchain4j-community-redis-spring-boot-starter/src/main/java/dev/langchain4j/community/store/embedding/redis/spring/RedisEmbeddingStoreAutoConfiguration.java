package dev.langchain4j.community.store.embedding.redis.spring;

import static dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

@AutoConfiguration
@EnableConfigurationProperties(RedisEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisEmbeddingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JedisClientConfig jedisClientConfig(RedisEmbeddingStoreProperties properties) {
        return DefaultJedisClientConfig.builder()
                .user(properties.getUser())
                .password(properties.getPassword())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public JedisPooled jedisPooled(RedisEmbeddingStoreProperties properties, JedisClientConfig jedisClientConfig) {
        return new JedisPooled(new HostAndPort(properties.getHost(), properties.getPort()), jedisClientConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisEmbeddingStore redisEmbeddingStore(
            RedisEmbeddingStoreProperties properties,
            @Nullable EmbeddingModel embeddingModel,
            @Nullable JedisPooled jedisPooled,
            @Nullable JedisClientConfig clientConfig) {
        return RedisEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .user(properties.getUser())
                .password(properties.getPassword())
                .jedisPooled(jedisPooled)
                .clientConfig(clientConfig)
                .prefix(properties.getPrefix())
                .indexName(properties.getIndexName())
                .dimension(Optional.ofNullable(embeddingModel)
                        .map(EmbeddingModel::dimension)
                        .orElse(properties.getDimension()))
                .metadataKeys(properties.getMetadataKeys())
                .build();
    }
}
