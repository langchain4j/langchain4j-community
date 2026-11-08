package dev.langchain4j.community.store.embedding.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuckDBEmbeddingStoreMetadataFilterTest {

    private static final Embedding EMBEDDING = new Embedding(new float[] {1, 0, 0});

    @Test
    void should_match_metadata_value_containing_a_single_quote() {
        DuckDBEmbeddingStore store = DuckDBEmbeddingStore.inMemory();
        add(store, "author", "O'Brien");
        add(store, "author", "Smith");

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest(new IsEqualTo("author", "O'Brien")));

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().metadata().getString("author"))
                .isEqualTo("O'Brien");
    }

    @Test
    void should_match_metadata_value_containing_a_single_quote_in_is_in() {
        DuckDBEmbeddingStore store = DuckDBEmbeddingStore.inMemory();
        add(store, "author", "O'Brien");
        add(store, "author", "Smith");

        EmbeddingSearchResult<TextSegment> result =
                store.search(searchRequest(new IsIn("author", List.of("O'Brien", "Nobody"))));

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().metadata().getString("author"))
                .isEqualTo("O'Brien");
    }

    @Test
    void should_match_metadata_key_containing_a_single_quote() {
        DuckDBEmbeddingStore store = DuckDBEmbeddingStore.inMemory();
        add(store, "it's", "kept");

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest(new IsEqualTo("it's", "kept")));

        assertThat(result.matches()).hasSize(1);
    }

    @Test
    void should_not_treat_a_metadata_value_as_sql() {
        DuckDBEmbeddingStore store = DuckDBEmbeddingStore.inMemory();
        add(store, "author", "Smith");

        // Inlined into the SQL text without escaping, this value would match every row.
        EmbeddingSearchResult<TextSegment> result =
                store.search(searchRequest(new IsEqualTo("author", "x' OR '1'='1")));

        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_remove_entries_whose_metadata_value_contains_a_single_quote() {
        DuckDBEmbeddingStore store = DuckDBEmbeddingStore.inMemory();
        add(store, "author", "O'Brien");
        add(store, "author", "Smith");

        store.removeAll(new IsEqualTo("author", "O'Brien"));

        assertThat(store.search(searchRequest(null)).matches()).hasSize(1);
    }

    private static void add(DuckDBEmbeddingStore store, String key, String value) {
        store.addAll(
                List.of(UUID.randomUUID().toString()),
                List.of(EMBEDDING),
                List.of(TextSegment.from("doc of " + value, new Metadata().put(key, value))));
    }

    private static EmbeddingSearchRequest searchRequest(Filter filter) {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING)
                .filter(filter)
                .maxResults(5)
                .build();
    }
}
