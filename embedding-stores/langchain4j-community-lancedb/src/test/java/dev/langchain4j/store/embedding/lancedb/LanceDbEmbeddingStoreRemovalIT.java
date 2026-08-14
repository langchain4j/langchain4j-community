package dev.langchain4j.store.embedding.lancedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.nio.file.Path;
import java.util.Map;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;
import org.lance.namespace.LanceNamespace;

/**
 * Disabled: {@code DirectoryNamespace.deleteFromTable} is not implemented in the native lance-core library
 * (confirmed unconditional across all published lance-core/lancedb-core versions up to 0.32.0-beta.2), so
 * every {@code remove}/{@code removeAll} call fails with
 * {@code UnsupportedOperationException: Not supported: delete_from_table not implemented}. This is an upstream
 * limitation, not a bug in {@link LanceDbEmbeddingStore}; re-enable once the native library implements deletion
 * for the directory namespace.
 */
@Disabled("DirectoryNamespace.deleteFromTable is not implemented upstream in lance-core; see class javadoc")
class LanceDbEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Map<String, String> config = Map.of("root", tempDir.toString());
        LanceNamespace namespace = LanceNamespace.connect("dir", config, new RootAllocator());
        String tableName = "test-removal-" + System.nanoTime();
        embeddingStore = new LanceDbEmbeddingStore(
                namespace, tableName, embeddingModel.dimension(), LanceDbEmbeddingStore.DistanceType.l2);
    }

    @AfterEach
    void tearDown() {
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

    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false;
    }

    @Override
    protected boolean supportsRemoveAll() {
        return true;
    }
}
