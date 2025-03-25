package dev.langchain4j.community.neo4j.spring;

import static dev.langchain4j.community.neo4j.spring.Neo4jEmbeddingStoreProperties.PREFIX;

import dev.langchain4j.community.store.embedding.neo4j.Builder;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

@AutoConfiguration
@EnableConfigurationProperties(Neo4jEmbeddingStoreProperties.class)
@ConditionalOnProperty(prefix = PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class Neo4jEmbeddingStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Neo4jEmbeddingStore neo4jEmbeddingStore(
            Neo4jEmbeddingStoreProperties properties, @Nullable EmbeddingModel embeddingModel) {

        final Neo4jEmbeddingStoreProperties.BasicAuth auth = properties.getAuth();
        final Builder builder = Neo4jEmbeddingStore.builder()
                .indexName(properties.getIndexName())
                .metadataPrefix(properties.getMetadataPrefix())
                .embeddingProperty(properties.getEmbeddingProperty())
                .idProperty(properties.getIdProperty())
                .label(properties.getLabel())
                .textProperty(properties.getTextProperty())
                .databaseName(properties.getDatabaseName())
                .retrievalQuery(properties.getRetrievalQuery())
                .config(properties.getConfig())
                .driver(properties.getDriver())
                .awaitIndexTimeout(properties.getAwaitIndexTimeout())
                .dimension(Optional.ofNullable(embeddingModel)
                        .map(EmbeddingModel::dimension)
                        .orElse(properties.getDimension()));
        if (auth != null) {
            builder.withBasicAuth(auth.getUri(), auth.getUser(), auth.getPassword());
        }
        return builder.build();
    }
}
