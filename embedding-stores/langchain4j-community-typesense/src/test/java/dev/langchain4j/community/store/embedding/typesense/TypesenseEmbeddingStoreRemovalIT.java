package dev.langchain4j.community.store.embedding.typesense;

import static java.util.Collections.singletonList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.typesense.TypesenseContainer;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

class TypesenseEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static TypesenseContainer typesense = new TypesenseContainer("typesense/typesense:27.1");

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    EmbeddingStore<TextSegment> embeddingStore = TypesenseEmbeddingStore.builder()
            .client(new Client(new Configuration(
                    singletonList(new Node("http", typesense.getHost(), typesense.getHttpPort())),
                    Duration.ofSeconds(10),
                    typesense.getApiKey())))
            .settings(TypesenseSettings.builder()
                    .collectionName("langchain4j_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE))
                    .dimension(embeddingModel.dimension())
                    .build())
            .build();

    @BeforeAll
    static void beforeAll() {
        typesense.start();
    }

    @AfterAll
    static void afterAll() {
        typesense.stop();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsRemoveAll() {
        // TODO: support remove all
        return false;
    }
}
