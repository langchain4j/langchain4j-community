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

    @Test
    void should_rebuild_index_after_every_addition_with_threshold_one() throws Exception {
        // Given: Create an embedding store with rebuildThreshold=1
        JVectorEmbeddingStore store = JVectorEmbeddingStore.builder()
                .dimension(384)
                .persistencePath(indexPath.toString())
                .rebuildThreshold(1) // Rebuild after every addition
                .build();

        Path graphPath = Path.of(indexPath + ".graph");
        Path metadataPath = Path.of(indexPath + ".metadata");

        // When: Add first embedding
        TextSegment segment1 = TextSegment.from("First embedding");
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        store.add(embedding1, segment1);

        // Small delay to ensure file system updates
        Thread.sleep(100);

        // Then: Index should be rebuilt and persisted after first addition
        assertThat(graphPath).exists();
        assertThat(metadataPath).exists();
        long firstModificationTime = Files.getLastModifiedTime(graphPath).toMillis();

        // When: Add second embedding
        Thread.sleep(100); // Ensure timestamps are different
        TextSegment segment2 = TextSegment.from("Second embedding");
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        store.add(embedding2, segment2);

        Thread.sleep(100);

        // Then: Index should be rebuilt again
        assertThat(graphPath).exists();
        long secondModificationTime = Files.getLastModifiedTime(graphPath).toMillis();
        assertThat(secondModificationTime).isGreaterThan(firstModificationTime);

        // Verify the store is functional
        Embedding queryEmbedding = embeddingModel.embed("First").content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .minScore(0.0)
                .build());

        assertThat(result.matches()).hasSize(2);
    }

    @Test
    void should_rebuild_index_periodically_with_larger_threshold() throws Exception {
        // Given: Create an embedding store with rebuildThreshold=3
        JVectorEmbeddingStore store = JVectorEmbeddingStore.builder()
                .dimension(384)
                .persistencePath(indexPath.toString())
                .rebuildThreshold(3) // Rebuild after every 3 additions
                .build();

        Path graphPath = Path.of(indexPath + ".graph");
        Path metadataPath = Path.of(indexPath + ".metadata");

        // When: Add first embedding
        TextSegment segment1 = TextSegment.from("First embedding");
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        store.add(embedding1, segment1);

        Thread.sleep(100);

        // Then: Index should NOT be built yet (threshold not reached)
        assertThat(graphPath).doesNotExist();

        // When: Add second embedding
        TextSegment segment2 = TextSegment.from("Second embedding");
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        store.add(embedding2, segment2);

        Thread.sleep(100);

        // Then: Index should still NOT be built (threshold not reached)
        assertThat(graphPath).doesNotExist();

        // When: Add third embedding
        TextSegment segment3 = TextSegment.from("Third embedding");
        Embedding embedding3 = embeddingModel.embed(segment3).content();
        store.add(embedding3, segment3);

        Thread.sleep(100);

        // Then: Index SHOULD be built now (threshold reached)
        assertThat(graphPath).exists();
        assertThat(metadataPath).exists();
        long firstRebuildTime = Files.getLastModifiedTime(graphPath).toMillis();

        // When: Add fourth embedding
        Thread.sleep(100);
        TextSegment segment4 = TextSegment.from("Fourth embedding");
        Embedding embedding4 = embeddingModel.embed(segment4).content();
        store.add(embedding4, segment4);

        Thread.sleep(100);

        // Then: Index should NOT be rebuilt yet (only 1 addition since last rebuild)
        long afterFourthAddition = Files.getLastModifiedTime(graphPath).toMillis();
        assertThat(afterFourthAddition).isEqualTo(firstRebuildTime);

        // When: Add fifth and sixth embeddings to reach threshold again
        Thread.sleep(100);
        TextSegment segment5 = TextSegment.from("Fifth embedding");
        Embedding embedding5 = embeddingModel.embed(segment5).content();
        store.add(embedding5, segment5);

        TextSegment segment6 = TextSegment.from("Sixth embedding");
        Embedding embedding6 = embeddingModel.embed(segment6).content();
        store.add(embedding6, segment6);

        Thread.sleep(100);

        // Then: Index SHOULD be rebuilt again (3 more additions since last rebuild)
        long secondRebuildTime = Files.getLastModifiedTime(graphPath).toMillis();
        assertThat(secondRebuildTime).isGreaterThan(firstRebuildTime);

        // Verify the store is functional with all embeddings
        Embedding queryEmbedding = embeddingModel.embed("embedding").content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.0)
                .build());

        assertThat(result.matches()).hasSize(6);
    }
}
