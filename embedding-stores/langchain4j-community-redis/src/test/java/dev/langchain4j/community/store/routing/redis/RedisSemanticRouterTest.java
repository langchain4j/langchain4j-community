package dev.langchain4j.community.store.routing.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.SearchResult;

class RedisSemanticRouterTest {

    private JedisPooled redis;
    private EmbeddingModel embeddingModel;
    private RedisSemanticRouter router;

    @BeforeEach
    void setUp() {
        // Create mocks
        redis = mock(JedisPooled.class, "MockRedis");
        embeddingModel = mock(EmbeddingModel.class);

        // Setup mock embedding model
        Embedding mockEmbedding = mock(Embedding.class);
        when(mockEmbedding.vector()).thenReturn(new float[] {0.1f, 0.2f, 0.3f});
        Response<Embedding> embeddingResponse = Response.from(mockEmbedding);
        when(embeddingModel.embed(any(String.class))).thenReturn(embeddingResponse);

        // Setup Redis mocks for proper test behavior
        // Mock ftList to avoid errors in ensureIndexExists
        Set<String> indexes = new HashSet<>();
        indexes.add("semantic-router-index"); // Add the default index name
        indexes.add("custom-router-index"); // Add a custom index name for the custom prefix test
        doReturn(indexes).when(redis).ftList();

        // Mock ScanResult for scan operations
        ScanResult<String> emptyScanResult = new ScanResult<>("0", Collections.emptyList());
        when(redis.scan(anyString(), any())).thenReturn(emptyScanResult);

        // Mock search results for ftSearch
        SearchResult emptySearchResult = mock(SearchResult.class);
        when(emptySearchResult.getDocuments()).thenReturn(Collections.emptyList());
        when(emptySearchResult.getTotalResults()).thenReturn(0L);
        // Specify the exact method signature to avoid ambiguity
        when(redis.ftSearch(anyString(), any(redis.clients.jedis.search.Query.class)))
                .thenReturn(emptySearchResult);

        // Create the router
        router = RedisSemanticRouter.builder()
                .redis(redis)
                .embeddingModel(embeddingModel)
                .build();
    }

    @Test
    void shouldRequireRedisAndEmbeddingModel() {
        assertThatThrownBy(() -> RedisSemanticRouter.builder()
                        .embeddingModel(embeddingModel)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redis client is required");

        assertThatThrownBy(() -> RedisSemanticRouter.builder().redis(redis).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embedding model is required");
    }

    @Test
    void shouldBuildWithDefaults() {
        RedisSemanticRouter router = RedisSemanticRouter.builder()
                .redis(redis)
                .embeddingModel(embeddingModel)
                .build();

        assertThat(router).isNotNull();
    }

    @Test
    void shouldBuildWithCustomSettings() {
        RedisSemanticRouter router = RedisSemanticRouter.builder()
                .redis(redis)
                .embeddingModel(embeddingModel)
                .prefix("custom-router")
                .maxResults(10)
                .build();

        assertThat(router).isNotNull();
    }

    @Test
    void shouldValidateRouteOnAdd() {
        assertThatThrownBy(() -> router.addRoute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route cannot be null");
    }

    @Test
    void shouldValidateRouteNameOnRemove() {
        assertThatThrownBy(() -> router.removeRoute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route name cannot be null");

        assertThatThrownBy(() -> router.removeRoute(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route name cannot be null or empty");
    }

    @Test
    void shouldValidateTextOnRoute() {
        assertThatThrownBy(() -> router.route(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Text cannot be null");

        assertThatThrownBy(() -> router.route(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Text cannot be null or empty");
    }

    @Test
    void shouldValidateRouteNameOnGetRoute() {
        assertThatThrownBy(() -> router.getRoute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route name cannot be null");

        assertThatThrownBy(() -> router.getRoute(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route name cannot be null or empty");
    }

    @Test
    void shouldReturnEmptyListForNoRoutes() {
        List<RouteMatch> matches = router.route("test query");
        assertThat(matches).isEmpty();
    }
}
