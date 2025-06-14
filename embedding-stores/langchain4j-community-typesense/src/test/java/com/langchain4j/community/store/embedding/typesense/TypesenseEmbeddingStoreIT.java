package com.langchain4j.community.store.embedding.typesense;

import dev.langchain4j.community.store.embedding.typesense.TypesenseEmbeddingStore;
import dev.langchain4j.community.store.embedding.typesense.TypesenseSchema;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.typesense.TypesenseContainer;
import org.typesense.api.Client;
import org.typesense.api.Configuration;
import org.typesense.resources.Node;

import java.time.Duration;
import java.util.List;
import java.util.Random;

class TypesenseEmbeddingStoreIT extends EmbeddingStoreIT {

    static TypesenseContainer typesense = new TypesenseContainer("typesense/typesense:27.1");

    Random random = new Random();

    EmbeddingStore<TextSegment> embeddingStore = TypesenseEmbeddingStore.builder()
            .client(new Client(new Configuration(
                    List.of(new Node("http", typesense.getHost(), typesense.getHttpPort())),
                    Duration.ofSeconds(2),
                    typesense.getApiKey())))
            .schema(TypesenseSchema.builder()
                    .collectionName("langchain4j_collection_" + random.nextInt(10000))
                    .build())
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
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
