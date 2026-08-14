package dev.langchain4j.community.store.embedding.vearch.spring;

import dev.langchain4j.community.store.embedding.vearch.VearchEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.context.ApplicationContext;

class VearchEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    static VearchContainer vearch = new VearchContainer();

    @BeforeAll
    static void beforeAll() {
        vearch.start();
    }

    @AfterAll
    static void afterAll() {
        vearch.stop();
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return VearchEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return VearchEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        String baseUrl = "http://" + vearch.getHost() + ":" + vearch.getMappedPort(9001);
        String databaseName = "embedding_db";
        String spaceName = "embedding_space_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        String embeddingFieldName = "text_embedding";
        String textFieldName = "text";

        return new String[] {
            "langchain4j.community.vearch.baseUrl=" + baseUrl,
            "langchain4j.community.vearch.logRequests=true",
            "langchain4j.community.vearch.logResponses=true",
            "langchain4j.community.vearch.config.databaseName=" + databaseName,
            "langchain4j.community.vearch.config.spaceName=" + spaceName,
            "langchain4j.community.vearch.config.embeddingFieldName=" + embeddingFieldName,
            "langchain4j.community.vearch.config.textFieldName=" + textFieldName,
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        return "langchain4j.community.vearch.config.dimension";
    }

    @Override
    protected void awaitUntilPersisted(ApplicationContext context) {
        try {
            Thread.sleep(3000);
        } catch (Exception e) {

        }
    }
}
