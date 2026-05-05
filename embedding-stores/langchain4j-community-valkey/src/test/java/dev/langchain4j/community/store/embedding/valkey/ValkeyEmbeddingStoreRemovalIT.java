package dev.langchain4j.community.store.embedding.valkey;

import static dev.langchain4j.community.store.embedding.valkey.ValkeySchema.JSON_PATH_PREFIX;
import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import glide.api.GlideClient;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.TagField;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;

class ValkeyEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    static GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey-extensions:8.1").withExposedPorts(6379);

    static GlideClient client;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    ValkeyEmbeddingStore embeddingStore;

    @BeforeAll
    static void beforeAll() throws ExecutionException, InterruptedException {
        valkey.start();
        client = GlideClient.createClient(GlideClientConfiguration.builder()
                        .address(NodeAddress.builder()
                                .host(valkey.getHost())
                                .port(valkey.getMappedPort(6379))
                                .build())
                        .build())
                .get();
    }

    @AfterAll
    static void afterAll() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // ignore
            }
        }
        valkey.stop();
    }

    ValkeyEmbeddingStoreRemovalIT() {
        embeddingStore = ValkeyEmbeddingStore.builder()
                .client(client)
                .indexName(randomUUID())
                .prefix(randomUUID() + ":")
                .dimension(embeddingModel.dimension())
                .metadataConfig(
                        Map.of("type", new FieldInfo(JSON_PATH_PREFIX + "type", "type", new TagField(',', true))))
                .build();
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
