package dev.langchain4j.community.store.embedding.memfile;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class MemFileEmbeddingStoreIt extends EmbeddingStoreWithFilteringIT {

    private EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private MemFileEmbeddingStore<TextSegment> embeddingStore = new MemFileEmbeddingStore<>(createTempDirectory());

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    public static Path createTempDirectory() {
        try {
            return Files.createTempDirectory(UUID.randomUUID().toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
