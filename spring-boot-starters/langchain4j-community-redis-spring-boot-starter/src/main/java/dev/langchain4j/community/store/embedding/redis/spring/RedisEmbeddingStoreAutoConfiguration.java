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

@AutoConfiguration
@EnableConfigurationProperties(RedisEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisEmbeddingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisEmbeddingStore redisEmbeddingStore(
            RedisEmbeddingStoreProperties properties, @Nullable EmbeddingModel embeddingModel) {
        return RedisEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .user(properties.getUser())
                .password(properties.getPassword())
                .prefix(properties.getPrefix())
                .indexName(properties.getIndexName())
                .dimension(Optional.ofNullable(embeddingModel)
                        .map(EmbeddingModel::dimension)
                        .orElse(properties.getDimension()))
                .metadataKeys(properties.getMetadataKeys())
                .build();
    }
}
