package dev.langchain4j.community.cockroachdb.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.testcontainers.containers.CockroachContainer;

class CockroachDbEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    static final CockroachContainer cockroach = new CockroachContainer("cockroachdb/cockroach:latest-v25.2");

    @BeforeAll
    static void beforeAll() {
        cockroach.start();
    }

    @AfterAll
    static void afterAll() {
        cockroach.stop();
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
                    CockroachDbEmbeddingStore store = context.getBean(CockroachDbEmbeddingStore.class);
                    assertThat(store).isNotNull();
                });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return CockroachDbEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return CockroachDbEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        return new String[] {
            "langchain4j.community.cockroachdb.connection-string=" + cockroach.getJdbcUrl(),
            "langchain4j.community.cockroachdb.username=" + cockroach.getUsername(),
            "langchain4j.community.cockroachdb.password=" + cockroach.getPassword(),
            "langchain4j.community.cockroachdb.table-name=" + "starter_it_"
                    + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE),
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.cockroachdb.dimension";
    }
}
