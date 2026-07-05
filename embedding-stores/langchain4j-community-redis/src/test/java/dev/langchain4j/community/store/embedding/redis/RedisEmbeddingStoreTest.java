package dev.langchain4j.community.store.embedding.redis;

import static dev.langchain4j.community.store.embedding.redis.RedisSchema.JSON_PATH_PREFIX;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.TagField;

@ExtendWith(MockitoExtension.class)
class RedisEmbeddingStoreTest {

    private static final String INDEX_NAME = "test-index";
    private static final String PREFIX = "embedding:";

    @Mock
    UnifiedJedis client;

    RedisEmbeddingStore store;

    @BeforeEach
    void setUp() {
        when(client.ftList()).thenReturn(Set.of(INDEX_NAME));
        store = RedisEmbeddingStore.builder()
                .unifiedJedis(client)
                .indexName(INDEX_NAME)
                .prefix(PREFIX)
                .dimension(384)
                .metadataConfig(
                        Map.of("type", TagField.of(JSON_PATH_PREFIX + "type").as("type")))
                .build();
    }

    @Test
    void should_not_call_del_when_remove_all_by_filter_matches_nothing() {
        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getDocuments()).thenReturn(List.of());
        when(client.ftSearch(eq(INDEX_NAME), any(Query.class))).thenReturn(searchResult);

        assertThatCode(() -> store.removeAll(metadataKey("type").isEqualTo("nonexistent")))
                .doesNotThrowAnyException();

        verify(client, never()).del(anyString());
    }

    @Test
    void should_call_del_when_remove_all_by_filter_matches_documents() {
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(PREFIX + "doc-1");

        SearchResult searchResult = mock(SearchResult.class);
        when(searchResult.getDocuments()).thenReturn(List.of(document));
        when(client.ftSearch(eq(INDEX_NAME), any(Query.class))).thenReturn(searchResult);

        store.removeAll(metadataKey("type").isEqualTo("a"));

        verify(client).del(PREFIX + "doc-1");
    }
}
