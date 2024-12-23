package dev.langchain4j.community.clickhouse.spring;

import dev.langchain4j.community.store.embedding.clickhouse.ClickHouseEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
