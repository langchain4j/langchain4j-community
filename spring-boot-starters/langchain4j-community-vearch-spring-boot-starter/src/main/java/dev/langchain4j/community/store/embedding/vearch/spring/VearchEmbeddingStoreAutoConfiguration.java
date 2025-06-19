package dev.langchain4j.community.store.embedding.vearch.spring;

import static dev.langchain4j.community.store.embedding.vearch.spring.VearchEmbeddingStoreProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.embedding.vearch.VearchConfig;
import dev.langchain4j.community.store.embedding.vearch.VearchEmbeddingStore;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@EnableConfigurationProperties(VearchEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class VearchEmbeddingStoreAutoConfiguration {

    private static final String EMBEDDING_STORE_HTTP_CLIENT_BUILDER = "vearchEmbeddingStoreHttpClientBuilder";

    @Bean
    @ConditionalOnMissingBean
    public VearchEmbeddingStore vearchEmbeddingStore(
            @Qualifier(EMBEDDING_STORE_HTTP_CLIENT_BUILDER) HttpClientBuilder httpClientBuilder,
            VearchEmbeddingStoreProperties properties,
            @Nullable EmbeddingModel embeddingModel) {
        VearchEmbeddingStoreProperties.Config config = properties.getConfig();
        return VearchEmbeddingStore.builder()
                .httpClientBuilder(httpClientBuilder)
                .customHeaders(properties.getCustomHeaders())
                .baseUrl(properties.getBaseUrl())
                .timeout(properties.getTimeout())
                .vearchConfig(VearchConfig.builder()
                        .databaseName(config.getDatabaseName())
                        .spaceName(config.getSpaceName())
                        .dimension(Optional.ofNullable(embeddingModel)
                                .map(EmbeddingModel::dimension)
                                .orElse(config.getDimension()))
                        .replicaNum(config.getReplicaNum())
                        .partitionNum(config.getPartitionNum())
                        .searchIndexParam(config.getSearchIndexParam())
                        .fields(config.getFields())
                        .embeddingFieldName(config.getEmbeddingFieldName())
                        .textFieldName(config.getTextFieldName())
                        .metadataFieldNames(config.getMetadataFieldNames())
                        .build())
                .normalizeEmbeddings(properties.isNormalizeEmbeddings())
                .logRequests(properties.isLogRequests())
                .logResponses(properties.isLogResponses())
                .build();
    }

    @Bean(EMBEDDING_STORE_HTTP_CLIENT_BUILDER)
    @ConditionalOnMissingBean(name = EMBEDDING_STORE_HTTP_CLIENT_BUILDER)
    HttpClientBuilder vearchEmbeddingStoreHttpClientBuilder(ObjectProvider<RestClient.Builder> restClientBuilder) {
        return SpringRestClient.builder()
                .restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder))
                // executor is not needed for no-streaming VearchEmbeddingStore
                .createDefaultStreamingRequestExecutor(false);
    }
}
