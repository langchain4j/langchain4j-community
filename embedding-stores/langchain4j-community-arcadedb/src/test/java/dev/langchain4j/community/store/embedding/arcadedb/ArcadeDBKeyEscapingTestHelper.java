package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.List;
import java.util.Map;

/**
 * Shared checks that metadata keys are quoted safely in SQL: a key cannot change the filter, and keys with a backtick
 * or a backslash work like any other key.
 */
final class ArcadeDBKeyEscapingTestHelper {

    static final int DIMENSION = 3;
    private static final String INJECTING_KEY = "tenant` = 'a' OR 1 = 1 OR `tenant";
    private static final String UNUSUAL_KEY = "we`ird\\key\\";

    private ArcadeDBKeyEscapingTestHelper() {}

    /** A key that closes the quoted name cannot turn a filter into an always-true condition, in search or removal. */
    static void assertKeyCannotChangeTheFilter(ArcadeDBEmbeddingStore store) {
        Embedding embedding = new Embedding(new float[] {1, 0, 0});
        for (String tenant : List.of("a", "b", "c")) {
            store.add(embedding, TextSegment.from("record " + tenant, new Metadata(Map.of("tenant", tenant))));
        }

        assertThat(search(store, embedding, INJECTING_KEY, "b")).isEmpty();

        store.removeAll(metadataKey(INJECTING_KEY).isEqualTo("b"));
        assertThat(search(store, embedding, null, null)).hasSize(3);
    }

    /** A key containing a backtick and backslashes can be stored, filtered on and removed by. */
    static void assertUnusualKeysWork(ArcadeDBEmbeddingStore store) {
        Embedding embedding = new Embedding(new float[] {0, 1, 0});
        store.add(embedding, TextSegment.from("unusual", new Metadata(Map.of(UNUSUAL_KEY, "v"))));
        store.add(embedding, TextSegment.from("usual", new Metadata(Map.of("other", "v"))));

        List<EmbeddingMatch<TextSegment>> found = search(store, embedding, UNUSUAL_KEY, "v");
        assertThat(found).extracting(m -> m.embedded().text()).containsExactly("unusual");
        assertThat(found.get(0).embedded().metadata().getString(UNUSUAL_KEY)).isEqualTo("v");

        store.removeAll(metadataKey(UNUSUAL_KEY).isEqualTo("v"));
        assertThat(search(store, embedding, null, null))
                .extracting(m -> m.embedded().text())
                .containsExactly("usual");
    }

    private static List<EmbeddingMatch<TextSegment>> search(
            ArcadeDBEmbeddingStore store, Embedding embedding, String key, String value) {
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder request =
                EmbeddingSearchRequest.builder().queryEmbedding(embedding).maxResults(10);
        if (key != null) {
            request.filter(metadataKey(key).isEqualTo(value));
        }
        return store.search(request.build()).matches();
    }
}
