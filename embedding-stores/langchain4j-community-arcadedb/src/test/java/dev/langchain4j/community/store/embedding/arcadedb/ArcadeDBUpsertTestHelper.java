package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Upsert semantics shared by the remote and embedded ITs: adding an id that exists replaces it, in both modes.
 * The store must be configured with batchSize(3).
 */
final class ArcadeDBUpsertTestHelper {

    private ArcadeDBUpsertTestHelper() {}

    static void assertUpsertAndReAdd(EmbeddingStore<TextSegment> store, EmbeddingModel model) {
        Embedding embedding = model.embed("upsert").content();

        store.addAll(List.of("x"), List.of(embedding), List.of(TextSegment.from("x v1")));
        store.addAll(List.of("x"), List.of(embedding), List.of(TextSegment.from("x v2")));
        assertThat(texts(store, embedding)).containsExactlyEntriesOf(Map.of("x", "x v2"));

        store.removeAll(List.of("x"));
        assertThat(texts(store, embedding)).isEmpty();
        store.addAll(List.of("x"), List.of(embedding), List.of(TextSegment.from("x v3")));
        assertThat(texts(store, embedding)).containsExactlyEntriesOf(Map.of("x", "x v3"));
    }

    static void assertRepeatedIdsInOneAddAll(EmbeddingStore<TextSegment> store, EmbeddingModel model) {
        Embedding embedding = model.embed("repeated").content();
        // With batchSize(3): "a" twice in the first batch, "b" in the first and in the third batch.
        List<String> ids = List.of("a", "a", "b", "c", "d", "e", "b");
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            embeddings.add(embedding);
            segments.add(TextSegment.from(ids.get(i) + " #" + i));
        }

        store.addAll(ids, embeddings, segments);

        assertThat(texts(store, embedding))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("a", "a #1", "b", "b #6", "c", "c #3", "d", "d #4", "e", "e #5"));
    }

    private static Map<String, String> texts(EmbeddingStore<TextSegment> store, Embedding embedding) {
        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(50)
                        .build())
                .matches();
        assertThat(matches).extracting(EmbeddingMatch::embeddingId).doesNotHaveDuplicates();
        return matches.stream()
                .collect(Collectors.toMap(
                        EmbeddingMatch::embeddingId, match -> match.embedded().text()));
    }
}
