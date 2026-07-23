package dev.langchain4j.store.embedding.lancedb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.nio.file.Path;
import java.util.Map;
import org.apache.arrow.memory.RootAllocator;
import org.junit.jupiter.api.io.TempDir;
import org.lance.namespace.LanceNamespace;

class LanceDbEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore;

    @TempDir
    Path tempDir;

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            Map<String, String> config = Map.of("root", tempDir.toString());
            LanceNamespace namespace = LanceNamespace.connect("dir", config, new RootAllocator());
            String tableName = "test-table-" + System.nanoTime();
            embeddingStore = new LanceDbEmbeddingStore(
                    namespace, tableName, embeddingModel.dimension(), LanceDbEmbeddingStore.DistanceType.l2);
        }
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        // no-op: embeddingStore() lazily creates a fresh table per test, so there is nothing to clear yet
    }

    @Override
    protected boolean supportsContains() {
        // Filters are evaluated client-side (see LanceDbEmbeddingStore#search), so any Filter type -
        // including ContainsString - works via Filter#test, regardless of native LanceDB query support.
        return true;
    }

    @Override
    protected boolean testFloatExactly() {
        return false;
    }

    @Override
    protected boolean testDoubleExactly() {
        return false;
    }
}
