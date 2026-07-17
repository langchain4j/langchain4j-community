package dev.langchain4j.community.clickhouse.spring;

import static dev.langchain4j.community.clickhouse.spring.ClickHouseEmbeddingStoreProperties.PREFIX;

import com.clickhouse.client.api.Client;
import dev.langchain4j.community.store.embedding.clickhouse.ClickHouseEmbeddingStore;
import dev.langchain4j.community.store.embedding.clickhouse.ClickHouseSettings;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ClickHouseEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClickHouseEmbeddingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClickHouseEmbeddingStore clickHouseEmbeddingStore(
            ClickHouseEmbeddingStoreProperties properties,
            ObjectProvider<Client> clientProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        return ClickHouseEmbeddingStore.builder()
                .client(clientProvider.getIfAvailable())
                .settings(ClickHouseSettings.builder()
                        .url(properties.getUrl())
                        .username(properties.getUsername())
                        .password(properties.getPassword())
                        .database(properties.getDatabase())
                        .table(properties.getTable())
                        .columnMap(properties.getColumnMap())
                        .metadataTypeMap(properties.getMetadataTypeMap())
                        .timeout(properties.getTimeout())
                        .dimension(Optional.ofNullable(embeddingModelProvider.getIfAvailable())
                                .map(EmbeddingModel::dimension)
                                .orElse(properties.getDimension()))
                        .build())
                .build();
    }
}
