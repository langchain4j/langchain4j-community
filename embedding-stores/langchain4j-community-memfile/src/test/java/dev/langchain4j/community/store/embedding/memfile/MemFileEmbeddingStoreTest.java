package dev.langchain4j.community.store.embedding.memfile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemFileEmbeddingStoreTest {

    private static final Embedding EMBEDDING = Embedding.from(new float[] {1, 0});
    private static final EmbeddingSearchRequest SEARCH = EmbeddingSearchRequest.builder()
            .queryEmbedding(EMBEDDING)
            .maxResults(10)
            .build();

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void should_reload_content_after_removing_a_cached_id(int cacheSize) {
        var store = new MemFileEmbeddingStore<TextSegment>(tempDir, cacheSize);
        TextSegment original = segment("old content", "old");
        TextSegment replacement = segment("new content", "new");
        store.add("doc-1", EMBEDDING, original);
        assertThat(contents(store)).containsExactly(original);

        store.removeAll(List.of("doc-1"));
        assertThat(contents(store)).isEmpty();
        store.add("doc-1", EMBEDDING, replacement);

        assertThat(contents(store)).containsExactly(replacement);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void should_reload_content_after_removing_by_filter(int cacheSize) {
        var store = new MemFileEmbeddingStore<TextSegment>(tempDir, cacheSize);
        TextSegment replacement = segment("new content", "new");
        TextSegment retained = segment("retained content", "keep");
        store.add("doc-1", EMBEDDING, segment("old content", "old"));
        store.add("doc-2", EMBEDDING, retained);

        // Filtering loads the content into the cache even without an earlier search.
        store.removeAll(new IsEqualTo("version", "old"));
        assertThat(contents(store)).containsExactly(retained);
        store.add("doc-1", EMBEDDING, replacement);

        assertThat(contents(store)).containsExactlyInAnyOrder(retained, replacement);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void should_remove_an_embedding_without_content(int cacheSize) {
        var store = new MemFileEmbeddingStore<TextSegment>(tempDir, cacheSize);
        store.add("vector-only", EMBEDDING);
        assertThat(store.search(SEARCH).matches()).hasSize(1);

        store.removeAll(List.of("vector-only"));

        assertThat(store.search(SEARCH).matches()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void should_reload_content_after_removing_all_entries(int cacheSize) {
        var store = new MemFileEmbeddingStore<TextSegment>(tempDir, cacheSize);
        TextSegment original = segment("old content", "old");
        TextSegment replacement = segment("new content", "new");
        store.add("doc-1", EMBEDDING, original);
        assertThat(contents(store)).containsExactly(original);

        store.removeAll();
        assertThat(contents(store)).isEmpty();
        store.add("doc-1", EMBEDDING, replacement);

        assertThat(contents(store)).containsExactly(replacement);
    }

    private static TextSegment segment(String text, String version) {
        return TextSegment.from(text, Metadata.from("version", version));
    }

    private static List<TextSegment> contents(MemFileEmbeddingStore<TextSegment> store) {
        return store.search(SEARCH).matches().stream()
                .map(EmbeddingMatch::embedded)
                .toList();
    }
}
