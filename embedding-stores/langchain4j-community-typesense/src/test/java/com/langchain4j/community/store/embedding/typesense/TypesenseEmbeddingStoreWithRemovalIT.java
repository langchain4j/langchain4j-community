package com.langchain4j.community.store.embedding.typesense;

import dev.langchain4j.community.store.embedding.typesense.TypesenseEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.typesense.TypesenseContainer;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.List;

class TypesenseEmbeddingStoreWithRemovalIT extends EmbeddingStoreWithRemovalIT {

    static TypesenseContainer typesense = new TypesenseContainer("typesense/typesense:27.1");

    EmbeddingStore<TextSegment> embeddingStore = TypesenseEmbeddingStore.builder()
            .client(new Client(new Configuration(
                    List.of(new Node("http", typesense.getHost(), typesense.getHttpPort())),
                    Duration.ofSeconds(2),
                    typesense.getApiKey())))
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

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
    protected boolean supportsRemoveAllByFilter() {
        return false;
    }
}
