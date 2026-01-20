package dev.langchain4j.community.store.embedding.oceanbase.spring;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.oceanbase.OceanBaseEmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.testcontainers.oceanbase.OceanBaseCEContainer;
import org.testcontainers.utility.DockerImageName;

class OceanBaseEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    static final String DEFAULT_USERNAME = "root@test";
    static final String DEFAULT_PASSWORD = "";
    static final String DEFAULT_DATABASE = "test";
    static final int OCEANBASE_PORT = 2881;

    static OceanBaseCEContainer oceanbase = new OceanBaseCEContainer(
                    DockerImageName.parse("oceanbase/oceanbase-ce:latest"))
            .withEnv("MODE", "slim")
            .withEnv("OB_DATAFILE_SIZE", "2G")
            .withExposedPorts(OCEANBASE_PORT);

    String tableName;

    @BeforeAll
    static void beforeAll() {
        oceanbase.start();
    }

    @AfterAll
    static void afterAll() {
        oceanbase.stop();
    }

    @BeforeEach
    void setTableName() {
        tableName = "test_table_" + randomUUID();
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
                    OceanBaseEmbeddingStore embeddingStore = context.getBean(OceanBaseEmbeddingStore.class);
                    assertThat(embeddingStore).isNotNull();
                });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return OceanBaseEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return OceanBaseEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        String jdbcUrl = "jdbc:oceanbase://" + oceanbase.getHost() + ":" + oceanbase.getMappedPort(OCEANBASE_PORT) + "/"
                + DEFAULT_DATABASE;
        return new String[] {
            "langchain4j.community.oceanbase.url=" + jdbcUrl,
            "langchain4j.community.oceanbase.user=" + DEFAULT_USERNAME,
            "langchain4j.community.oceanbase.password=" + DEFAULT_PASSWORD,
            "langchain4j.community.oceanbase.table-name=" + tableName
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.oceanbase.dimension";
    }
}
