package dev.langchain4j.store.embedding.lancedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.lance.namespace.LanceNamespace;

class LanceDbEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static Path tempDir;
    private static LanceNamespace namespace;

    private EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private LanceDbEmbeddingStore embeddingStore = createEmbeddingStore();

    @BeforeAll
    static void beforeAll() throws IOException {
        tempDir = Files.createTempDirectory("lancedb-removal-it");
        Map<String, String> config = new HashMap<>();
        config.put("root", tempDir.toString());
        namespace = LanceNamespace.connect("dir", config, new RootAllocator());
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (namespace instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        deleteRecursively(tempDir);
    }

    @BeforeEach
    void clearStore() {
        embeddingStore = createEmbeddingStore();
    }

    private LanceDbEmbeddingStore createEmbeddingStore() {
        String tableName = "test_removal_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        return new LanceDbEmbeddingStore(
                namespace, tableName, embeddingModel.dimension(), LanceDbEmbeddingStore.DistanceType.l2);
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

    @Override
    protected boolean supportsRemoveAll() {
        return true;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walk(path).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException ignored) {
            }
        });
    }
}
