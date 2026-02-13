package dev.langchain4j.community.store.embedding.neo4j;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore.SearchType.MATCH_SEARCH_CLAUSE;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

class Neo4jEmbeddingStoreWith2026SyntaxTest extends Neo4jEmbeddingStoreBaseTest {
    
    @Test
    void should_search_using_match_search_syntax() {
        String gqlLabel = "GqlDocSimple";
        EmbeddingStore<TextSegment> gqlStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(gqlLabel)
                .indexName("new_syntax")
                .searchType(MATCH_SEARCH_CLAUSE)
                .filterMetadata(Collections.emptyList()) // Explicitly no metadata
                .build();

        TextSegment segment = TextSegment.from("Hello GQL");
        Embedding embedding = embeddingModel.embed(segment).content();
        gqlStore.add(embedding, segment);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();

        EmbeddingSearchResult<TextSegment> result = gqlStore.search(request);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("Hello GQL");
    }

    @Test
    void should_search_using_match_search_syntax_no_metadata() {
        String gqlLabel = "GqlDocNoMeta";
        EmbeddingStore<TextSegment> gqlStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(gqlLabel)
                .indexName("new_syntax_2")
                .searchType(MATCH_SEARCH_CLAUSE)
                // .filterMetadata() is NOT called, implicitly empty
                .build();

        TextSegment segment = TextSegment.from("Pure Vector Search");
        Embedding embedding = embeddingModel.embed(segment).content();
        gqlStore.add(embedding, segment);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build();

        EmbeddingSearchResult<TextSegment> result = gqlStore.search(request);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("Pure Vector Search");
    }

    @Test
    void should_search_using_match_search_syntax_with_filters() {
        String gqlLabel = "GqlDocFilter";
        EmbeddingStore<TextSegment> gqlStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(gqlLabel)
                .indexName("new_syntax_3")
                .searchType(MATCH_SEARCH_CLAUSE)
                .filterMetadata(Arrays.asList("category")) // Enable filtering for 'category'
                .build();

        // Data prep
        TextSegment segment1 = TextSegment.from("Apple iPhone", Metadata.from("category", "electronics"));
        Embedding embedding1 = embeddingModel.embed(segment1).content();

        TextSegment segment2 = TextSegment.from("Apple Pie", Metadata.from("category", "food"));
        Embedding embedding2 = embeddingModel.embed(segment2).content();

        gqlStore.addAll(List.of(embedding1, embedding2), List.of(segment1, segment2));

        // Search for "Apple" but filter for "food" using GQL syntax
        Filter filter = metadataKey("category").isEqualTo("food");
        Embedding queryEmbedding = embeddingModel.embed("Apple").content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(10)
                .build();

        EmbeddingSearchResult<TextSegment> result = gqlStore.search(request);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("Apple Pie");
    }
}
