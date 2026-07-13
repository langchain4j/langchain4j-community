package dev.langchain4j.community.store.embedding.s3.spring;

import static dev.langchain4j.community.store.embedding.s3.spring.S3VectorsEmbeddingStoreProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.embedding.s3.S3VectorsEmbeddingStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

@AutoConfiguration
@ConditionalOnClass(S3VectorsEmbeddingStore.class)
@EnableConfigurationProperties(S3VectorsEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class S3VectorsEmbeddingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public S3VectorsEmbeddingStore s3VectorsEmbeddingStore(
            S3VectorsEmbeddingStoreProperties properties, ObjectProvider<S3VectorsClient> s3VectorsClientProvider) {

        S3VectorsEmbeddingStore.Builder builder = S3VectorsEmbeddingStore.builder()
                .vectorBucketName(properties.getVectorBucketName())
                .indexName(properties.getIndexName());

        // No credential properties: without a user-provided client the store falls back to the AWS
        // SDK default credentials provider chain.
        S3VectorsClient s3VectorsClient = s3VectorsClientProvider.getIfAvailable();
        if (s3VectorsClient != null) {
            builder.s3VectorsClient(s3VectorsClient);
        }
        if (properties.getRegion() != null) {
            builder.region(properties.getRegion());
        }
        if (properties.getDistanceMetric() != null) {
            builder.distanceMetric(properties.getDistanceMetric());
        }
        if (properties.getCreateIndexIfNotExists() != null) {
            builder.createIndexIfNotExists(properties.getCreateIndexIfNotExists());
        }
        if (properties.getTextMetadataKey() != null) {
            builder.textMetadataKey(properties.getTextMetadataKey());
        }
        if (properties.getTimeout() != null) {
            builder.timeout(properties.getTimeout());
        }
        return builder.build();
    }
}
