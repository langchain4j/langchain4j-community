package dev.langchain4j.store.embedding.lancedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.lance.namespace.LanceNamespace;

class LanceDbEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static Path tempDir;
    private static LanceNamespace namespace;

    private EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private LanceDbEmbeddingStore embeddingStore = createEmbeddingStore();

    @BeforeAll
    static void beforeAll() throws IOException {
        tempDir = Files.createTempDirectory("lancedb-it");
        Map<String, String> config = new HashMap<>();
        config.put("root", tempDir.toAbsolutePath().toString());
        config.put("uri", tempDir.toUri().toString());
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

    private LanceDbEmbeddingStore createEmbeddingStore() {
        String tableName = "test_table_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
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
    protected void clearStore() {
        embeddingStore = createEmbeddingStore();
    }

    @Override
    protected boolean supportsContains() {
        return false;
    }

    @Override
    protected boolean testFloatExactly() {
        return false;
    }

    @Override
    protected boolean testDoubleExactly() {
        return false;
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
