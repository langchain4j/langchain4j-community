package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

final class ArcadeDBEmbeddingStoreHybridSearchTestHelper {

    private ArcadeDBEmbeddingStoreHybridSearchTestHelper() {}

    static void assertHybridSearch(EmbeddingStore<TextSegment> store, EmbeddingModel model) {
        TextSegment segment1 = TextSegment.from("The quick brown fox jumps over the lazy dog");
        TextSegment segment2 = TextSegment.from("Java programming language is popular");
        TextSegment segment3 = TextSegment.from("ArcadeDB is a graph database with vector search support");

        Embedding embedding1 = model.embed(segment1.text()).content();
        Embedding embedding2 = model.embed(segment2.text()).content();
        Embedding embedding3 = model.embed(segment3.text()).content();

        store.add(embedding1, segment1);
        store.add(embedding2, segment2);
        store.add(embedding3, segment3);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding3)
                .query("database")
                .maxResults(3)
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);

        assertThat(result.matches()).isNotEmpty();
        assertThat(result.matches().get(0).embedded().text()).contains("database");
    }
}
