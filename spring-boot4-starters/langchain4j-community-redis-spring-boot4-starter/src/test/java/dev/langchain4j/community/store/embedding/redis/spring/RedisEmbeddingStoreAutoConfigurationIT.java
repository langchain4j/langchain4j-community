package dev.langchain4j.community.store.embedding.redis.spring;

import static com.redis.testcontainers.RedisStackContainer.DEFAULT_IMAGE_NAME;
import static com.redis.testcontainers.RedisStackContainer.DEFAULT_TAG;
import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisStackContainer;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
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

class RedisEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    static final String PASSWORD = "redis-stack";

    static RedisStackContainer redis = new RedisStackContainer(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG))
            .withEnv("REDIS_ARGS", "--requirepass %s".formatted(PASSWORD));

    String indexName;

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
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
                    RedisEmbeddingStore embeddingStore = context.getBean(RedisEmbeddingStore.class);
                    assertThat(embeddingStore).isNotNull();
                    assertThat(embeddingStore.getSchema().getDimension()).isEqualTo(embeddingModel.dimension());
                });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return RedisEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return RedisEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        return new String[] {
            "langchain4j.community.redis.host=" + redis.getHost(),
            "langchain4j.community.redis.port=" + redis.getFirstMappedPort(),
            "langchain4j.community.redis.password=" + PASSWORD,
            "langchain4j.community.redis.prefix=" + indexName + ":",
            "langchain4j.community.redis.index-name=" + indexName
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.redis.dimension";
    }
}
