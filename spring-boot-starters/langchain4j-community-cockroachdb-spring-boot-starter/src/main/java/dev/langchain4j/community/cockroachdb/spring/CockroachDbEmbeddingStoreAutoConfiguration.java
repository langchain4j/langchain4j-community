package dev.langchain4j.community.cockroachdb.spring;

import static dev.langchain4j.community.cockroachdb.spring.CockroachDbEmbeddingStoreProperties.PREFIX;

import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEmbeddingStore;
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEngine;
import dev.langchain4j.community.store.embedding.cockroachdb.MetricType;
import dev.langchain4j.community.store.embedding.cockroachdb.index.BaseIndex;
import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;
import dev.langchain4j.community.store.embedding.cockroachdb.index.NoIndex;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CockroachDbEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class CockroachDbEmbeddingStoreAutoConfiguration {

    private static BaseIndex resolveIndex(CockroachDbEmbeddingStoreProperties properties) {
        String type = properties.getIndexType();
        if (type == null) return null;
        switch (type.toLowerCase()) {
            case "cspann":
                CSpannIndex.Builder b = CSpannIndex.builder();
                if (properties.getMinPartitionSize() != null) b.minPartitionSize(properties.getMinPartitionSize());
                if (properties.getMaxPartitionSize() != null) b.maxPartitionSize(properties.getMaxPartitionSize());
                return b.build();
            case "none":
                return new NoIndex();
            default:
                throw new IllegalArgumentException("Unknown indexType '" + type + "', expected one of: cspann, none");
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public CockroachDbEngine cockroachDbEngine(CockroachDbEmbeddingStoreProperties properties) {
        CockroachDbEngine.Builder builder = CockroachDbEngine.builder();
        if (properties.getConnectionString() != null) {
            builder.connectionString(properties.getConnectionString());
        }
        if (properties.getHost() != null) builder.host(properties.getHost());
        if (properties.getPort() != null) builder.port(properties.getPort());
        if (properties.getDatabase() != null) builder.database(properties.getDatabase());
        if (properties.getUsername() != null) builder.username(properties.getUsername());
        if (properties.getPassword() != null) builder.password(properties.getPassword());
        if (properties.getSslMode() != null) builder.sslMode(properties.getSslMode());
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public CockroachDbEmbeddingStore cockroachDbEmbeddingStore(
            CockroachDbEmbeddingStoreProperties properties,
            CockroachDbEngine engine,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {

        CockroachDbEmbeddingStore.Builder builder = CockroachDbEmbeddingStore.builder()
                .engine(engine)
                .dimension(Optional.ofNullable(embeddingModelProvider.getIfAvailable())
                        .map(EmbeddingModel::dimension)
                        .orElse(properties.getDimension()));

        if (properties.getTableName() != null) builder.tableName(properties.getTableName());
        if (properties.getSchemaName() != null) builder.schemaName(properties.getSchemaName());
        if (properties.getMetricType() != null) {
            builder.metricType(MetricType.valueOf(properties.getMetricType().toUpperCase()));
        }
        if (properties.getNamespaceColumn() != null) builder.namespaceColumn(properties.getNamespaceColumn());
        if (properties.getNamespace() != null) builder.namespace(properties.getNamespace());
        if (properties.getSearchBeamSize() != null) builder.searchBeamSize(properties.getSearchBeamSize());

        BaseIndex index = resolveIndex(properties);
        if (index != null) builder.vectorIndex(index);

        return builder.build();
    }
}
