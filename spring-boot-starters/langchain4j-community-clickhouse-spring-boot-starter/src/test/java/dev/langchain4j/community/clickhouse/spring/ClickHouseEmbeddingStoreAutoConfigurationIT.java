package dev.langchain4j.community.clickhouse.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.community.store.embedding.clickhouse.ClickHouseEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

class ClickHouseEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    private static final String USERNAME = "test-username";
    private static final String PASSWORD = "test-password";

    static ClickHouseContainer clickhouse = new ClickHouseContainer(
                    DockerImageName.parse("clickhouse/clickhouse-server:latest"))
            .withDatabaseName("default")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @BeforeAll
    static void beforeAll() {
        clickhouse.start();
    }

    @AfterAll
    static void afterAll() {
        clickhouse.stop();
    }

    @Test
    void should_respect_metadata_type_map() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        String[] metadataProperties = new String[] {
            "langchain4j.community.clickhouse.metadata-type-map.age=Int32",
            "langchain4j.community.clickhouse.metadata-type-map.country=String",
            "langchain4j.community.clickhouse.metadata-type-map.city=String"
        };
        String[] properties = new String[properties().length + metadataProperties.length + 1];
        System.arraycopy(properties(), 0, properties, 0, properties().length);
        System.arraycopy(metadataProperties, 0, properties, properties().length, metadataProperties.length);
        properties[properties.length - 1] = dimensionPropertyKey() + "=" + embeddingModel.dimension();

        contextRunner.withPropertyValues(properties).run(context -> {
            TextSegment segment = TextSegment.from(
                    "hello",
                    Metadata.from(Map.of(
                            "age", 32,
                            "country", "UK",
                            "city", "London")));
            Embedding embedding = embeddingModel.embed(segment.text()).content();

            assertThat(context.getBean(embeddingStoreClass())).isExactlyInstanceOf(embeddingStoreClass());
            EmbeddingStore<TextSegment> embeddingStore = context.getBean(embeddingStoreClass());

            String id = embeddingStore.add(embedding, segment);
            assertThat(id).isNotBlank();

            awaitUntilPersisted(context);

            List<EmbeddingMatch<TextSegment>> relevant = embeddingStore
                    .search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding)
                            .maxResults(10)
                            .build())
                    .matches();
            assertThat(relevant).hasSize(1);

            EmbeddingMatch<TextSegment> match = relevant.get(0);
            assertThat(match.score()).isCloseTo(1, withPercentage(1));
            assertThat(match.embeddingId()).isEqualTo(id);
            assertThat(match.embedding()).isEqualTo(embedding);
            assertThat(match.embedded()).isEqualTo(segment);
        });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return ClickHouseEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return ClickHouseEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        return new String[] {
            "langchain4j.community.clickhouse.url=" + "http://" + clickhouse.getHost() + ":"
                    + clickhouse.getMappedPort(8123),
            "langchain4j.community.clickhouse.table=" + "langchain4j_"
                    + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE),
            "langchain4j.community.clickhouse.username=" + USERNAME,
            "langchain4j.community.clickhouse.password=" + PASSWORD
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.clickhouse.dimension";
    }
}
