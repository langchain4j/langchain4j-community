package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.Map;

/**
 * Shared checks that the type name is quoted safely in SQL: a type name with a backtick, a single quote and a space
 * works in every operation (it is used both as an identifier and inside the index names passed as strings).
 */
final class ArcadeDBTypeNameEscapingTestHelper {

    static final int DIMENSION = 3;
    /** No backslash: embedded mode names its files after the type, and a backslash is not portable in file names. */
    static final String UNUSUAL_TYPE_NAME = "We`ird 'Type";

    private static final Embedding EMBEDDING = new Embedding(new float[] {1, 0, 0});

    private ArcadeDBTypeNameEscapingTestHelper() {}

    static void assertUnusualTypeNameWorks(ArcadeDBEmbeddingStore store) {
        String id = store.add(EMBEDDING, TextSegment.from("alpha zebra", new Metadata(Map.of("tenant", "a"))));
        store.addAll(
                List.of(id, "second"),
                List.of(EMBEDDING, new Embedding(new float[] {0, 1, 0})),
                List.of(
                        TextSegment.from("alpha zebra again", new Metadata(Map.of("tenant", "a"))),
                        TextSegment.from("beta", new Metadata(Map.of("tenant", "b")))));

        assertThat(texts(search(store, null, null))).containsExactly("alpha zebra again", "beta");
        assertThat(texts(search(store, null, metadataKey("tenant").isEqualTo("b"))))
                .containsExactly("beta");
        assertThat(texts(search(store, "zebra", null))).startsWith("alpha zebra again");
        assertThat(texts(search(store, "zebra", metadataKey("tenant").isEqualTo("b"))))
                .containsExactly("beta");

        store.removeAll(metadataKey("tenant").isEqualTo("b"));
        assertThat(texts(search(store, null, null))).containsExactly("alpha zebra again");

        store.remove(id);
        assertThat(search(store, null, null)).isEmpty();

        store.add(EMBEDDING, TextSegment.from("gamma"));
        store.removeAll();
        assertThat(search(store, null, null)).isEmpty();
    }

    private static List<EmbeddingMatch<TextSegment>> search(ArcadeDBEmbeddingStore store, String query, Filter filter) {
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder request =
                EmbeddingSearchRequest.builder().queryEmbedding(EMBEDDING).maxResults(10);
        if (query != null) {
            request.query(query);
        }
        if (filter != null) {
            request.filter(filter);
        }
        return store.search(request.build()).matches();
    }

    private static List<String> texts(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream().map(m -> m.embedded().text()).toList();
    }
}
