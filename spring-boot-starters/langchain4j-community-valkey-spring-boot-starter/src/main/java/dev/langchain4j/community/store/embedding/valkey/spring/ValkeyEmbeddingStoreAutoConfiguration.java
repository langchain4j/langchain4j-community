package dev.langchain4j.community.store.embedding.valkey.spring;

import static dev.langchain4j.community.store.embedding.valkey.spring.ValkeyEmbeddingStoreProperties.CONFIG_PREFIX;

import dev.langchain4j.community.store.embedding.valkey.ValkeyEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ValkeyEmbeddingStore.class)
@EnableConfigurationProperties(ValkeyEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class ValkeyEmbeddingStoreAutoConfiguration {

    private static final long CLIENT_CONNECT_TIMEOUT_SECONDS = 10;

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "host")
    public GlideClient glideClient(ValkeyEmbeddingStoreProperties properties) {
        try {
            GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> configBuilder =
                    GlideClientConfiguration.builder()
                            .address(NodeAddress.builder()
                                    .host(properties.getHost())
                                    .port(properties.getPort())
                                    .build());

            if (properties.getPassword() != null) {
                ServerCredentials.ServerCredentialsBuilder credentialsBuilder =
                        ServerCredentials.builder().password(properties.getPassword());
                if (properties.getUsername() != null) {
                    credentialsBuilder.username(properties.getUsername());
                }
                configBuilder.credentials(credentialsBuilder.build());
            }

            if (Boolean.TRUE.equals(properties.getUseTls())) {
                configBuilder.useTLS(true);
            }

            if (properties.getRequestTimeout() != null) {
                configBuilder.requestTimeout(properties.getRequestTimeout());
            }

            if (properties.getClientName() != null) {
                configBuilder.clientName(properties.getClientName());
            }

            return GlideClient.createClient(configBuilder.build())
                    .get(CLIENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BeanCreationException(
                    "Timed out connecting to Valkey at "
                            + properties.getHost() + ":" + properties.getPort()
                            + " after " + CLIENT_CONNECT_TIMEOUT_SECONDS + " seconds",
                    e);
        } catch (ExecutionException e) {
            throw new BeanCreationException(
                    "Failed to connect to Valkey at " + properties.getHost() + ":" + properties.getPort(),
                    e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BeanCreationException("Interrupted while connecting to Valkey", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ValkeyEmbeddingStore valkeyEmbeddingStore(
            ValkeyEmbeddingStoreProperties properties,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectProvider<GlideClient> glideClientProvider) {
        return ValkeyEmbeddingStore.builder()
                .client(glideClientProvider.getIfAvailable())
                .indexName(properties.getIndexName())
                .prefix(properties.getPrefix())
                .dimension(Optional.ofNullable(embeddingModelProvider.getIfAvailable())
                        .map(EmbeddingModel::dimension)
                        .orElse(properties.getDimension()))
                .metadataKeys(properties.getMetadataKeys())
                .operationTimeoutSeconds(properties.getOperationTimeoutSeconds())
                .build();
    }
}
