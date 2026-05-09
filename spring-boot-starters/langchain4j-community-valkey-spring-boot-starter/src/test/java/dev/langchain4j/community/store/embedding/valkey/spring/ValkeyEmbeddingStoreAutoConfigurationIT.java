package dev.langchain4j.community.store.embedding.valkey.spring;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.valkey.ValkeyEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.testcontainers.containers.GenericContainer;

class ValkeyEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey-extensions:8.1").withExposedPorts(6379);

    String indexName;

    @BeforeAll
    static void beforeAll() {
        valkey.start();
    }

    @AfterAll
    static void afterAll() {
        valkey.stop();
    }

    @BeforeEach
    void setIndexName() {
        indexName = randomUUID();
    }

    @Test
    void should_respect_embedding_model_bean() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(TestEmbeddingModelAutoConfiguration.class))
                .withPropertyValues(properties())
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel)
                            .isNotNull()
                            .isExactlyInstanceOf(AllMiniLmL6V2QuantizedEmbeddingModel.class);
                    ValkeyEmbeddingStore embeddingStore = context.getBean(ValkeyEmbeddingStore.class);
                    assertThat(embeddingStore).isNotNull();
                });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return ValkeyEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return ValkeyEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        return new String[] {
            "langchain4j.community.valkey.host=" + valkey.getHost(),
            "langchain4j.community.valkey.port=" + valkey.getMappedPort(6379),
            "langchain4j.community.valkey.prefix=" + indexName + ":",
            "langchain4j.community.valkey.index-name=" + indexName
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.valkey.dimension";
    }
}
