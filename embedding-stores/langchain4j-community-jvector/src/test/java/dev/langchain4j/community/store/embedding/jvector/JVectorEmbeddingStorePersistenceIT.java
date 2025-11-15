package dev.langchain4j.community.store.embedding.jvector;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JVectorEmbeddingStorePersistenceIT {

    @TempDir
    Path tempDir;

    private Path indexPath;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        indexPath = tempDir.resolve("test-index");
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        Files.deleteIfExists(Path.of(indexPath + ".graph"));
        Files.deleteIfExists(Path.of(indexPath + ".metadata"));
    }

    @Test
    void should_persist_and_load_embeddings() {
        // Given: Create an embedding store with persistence
        JVectorEmbeddingStore store1 = JVectorEmbeddingStore.builder()
                .dimension(384)
                .persistencePath(indexPath.toString())
                .build();

        // When: Add some embeddings
        TextSegment segment1 = TextSegment.from("Hello world");
        TextSegment segment2 = TextSegment.from("How are you?");
        TextSegment segment3 = TextSegment.from("Good morning");

        Embedding embedding1 = embeddingModel.embed(segment1).content();
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        Embedding embedding3 = embeddingModel.embed(segment3).content();

        String id1 = store1.add(embedding1, segment1);
        String id2 = store1.add(embedding2, segment2);
        String id3 = store1.add(embedding3, segment3);

        // Save to disk
        store1.save();

        // Then: Create a new store from the same path and verify data is loaded
        JVectorEmbeddingStore store2 = JVectorEmbeddingStore.builder()
                .dimension(384)
                .persistencePath(indexPath.toString())
                .build();

        // Perform a search to verify the index is loaded and functional
        Embedding queryEmbedding = embeddingModel.embed("Hello").content();
        EmbeddingSearchResult<TextSegment> result = store2.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.0)
                .build());

        // Verify we got results
        assertThat(result.matches()).hasSize(3);

        // Verify the IDs and segments are preserved
        List<String> resultIds =
                result.matches().stream().map(EmbeddingMatch::embeddingId).toList();

        assertThat(resultIds).containsExactlyInAnyOrder(id1, id2, id3);

        // Verify text segments are preserved
        List<String> resultTexts = result.matches().stream()
                .map(match -> match.embedded() != null ? match.embedded().text() : null)
                .toList();

        assertThat(resultTexts).containsExactlyInAnyOrder("Hello world", "How are you?", "Good morning");
    }

    @Test
    void should_work_without_persistence() {
        // Given: Create an embedding store without persistence
        JVectorEmbeddingStore store =
                JVectorEmbeddingStore.builder().dimension(384).build();

        // When: Add some embeddings
        TextSegment segment = TextSegment.from("Test");
        Embedding embedding = embeddingModel.embed(segment).content();
        String id = store.add(embedding, segment);

        // Then: Should be able to search
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .minScore(0.0)
                .build());

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embeddingId()).isEqualTo(id);
    }

    @Test
    void should_handle_empty_store_persistence() {
        // Given: Create an empty embedding store with persistence
        JVectorEmbeddingStore store1 = JVectorEmbeddingStore.builder()
                .dimension(384)
                .persistencePath(indexPath.toString())
                .build();

        // Add and remove all to create an empty state
        TextSegment segment = TextSegment.from("Test");
        Embedding embedding = embeddingModel.embed(segment).content();
        store1.add(embedding, segment);
        store1.removeAll();

        // When: Try to save empty store (should handle gracefully)
        // Note: save() will fail if there are no vectors, which is expected behavior
        try {
            store1.save();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Cannot save an empty embedding store");
        }

        // Then: Create a new store from the same path (should start empty)
        JVectorEmbeddingStore store2 = JVectorEmbeddingStore.builder()
                .dimension(384)
                .persistencePath(indexPath.toString())
                .build();

        // Should be able to add new embeddings
        String newId = store2.add(embedding, segment);
        assertThat(newId).isNotNull();
    }
}
