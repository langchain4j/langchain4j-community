package dev.langchain4j.community.store.embedding.oceanbase.spring;

import static dev.langchain4j.community.store.embedding.oceanbase.spring.OceanBaseEmbeddingStoreProperties.CONFIG_PREFIX;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.oceanbase.OceanBaseEmbeddingStore;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(OceanBaseEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class OceanBaseEmbeddingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OceanBaseEmbeddingStore oceanBaseEmbeddingStore(
            OceanBaseEmbeddingStoreProperties properties, ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        OceanBaseEmbeddingStore.Builder builder = OceanBaseEmbeddingStore.builder()
                .url(properties.getUrl())
                .user(properties.getUser())
                .password(properties.getPassword())
                .tableName(properties.getTableName())
                .dimension(Optional.ofNullable(embeddingModelProvider.getIfAvailable())
                        .map(EmbeddingModel::dimension)
                        .orElse(properties.getDimension()));

        if (properties.getMetricType() != null) {
            builder.metricType(properties.getMetricType());
        }
        if (properties.getRetrieveEmbeddingsOnSearch() != null) {
            builder.retrieveEmbeddingsOnSearch(properties.getRetrieveEmbeddingsOnSearch());
        }
        if (properties.getIdFieldName() != null) {
            builder.idFieldName(properties.getIdFieldName());
        }
        if (properties.getTextFieldName() != null) {
            builder.textFieldName(properties.getTextFieldName());
        }
        if (properties.getMetadataFieldName() != null) {
            builder.metadataFieldName(properties.getMetadataFieldName());
        }
        if (properties.getVectorFieldName() != null) {
            builder.vectorFieldName(properties.getVectorFieldName());
        }
        if (properties.getEnableHybridSearch() != null) {
            builder.enableHybridSearch(properties.getEnableHybridSearch());
        }

        return builder.build();
    }
}
