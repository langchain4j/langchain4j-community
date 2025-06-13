package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EmbeddingModelCacheTest {

    @Mock
    private EmbeddingModel mockModel;

    @Mock
    private EmbeddingCache mockCache;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        // Reset global state
        EmbeddingModelCache.disableGlobalCache();
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();
        // Clean up after each test
        EmbeddingModelCache.disableGlobalCache();
    }

    @Test
    public void should_passthrough_model_when_caching_disabled() {
        // given
        String text = "test text";
        Embedding testEmbedding = Embedding.from(new float[] {0.1f, 0.2f, 0.3f});
        Response<Embedding> expectedResponse = Response.from(testEmbedding);

        when(mockModel.embed(text)).thenReturn(expectedResponse);

        // when
        EmbeddingModel model = EmbeddingModelCache.wrap(mockModel);
        Response<Embedding> actualResponse = model.embed(text);

        // then
        assertThat(model).isSameAs(mockModel);
        assertThat(actualResponse).isSameAs(expectedResponse);
    }

    @Test
    public void should_wrap_model_when_caching_enabled() {
        // given
        EmbeddingModelCache.configureGlobalCache(mockCache);

        // when
        EmbeddingModel wrappedModel = EmbeddingModelCache.wrap(mockModel);

        // then
        assertThat(wrappedModel).isInstanceOf(CachedEmbeddingModel.class);
        assertThat(wrappedModel).isNotSameAs(mockModel);

        CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) wrappedModel;
        assertThat(cachedModel.getDelegate()).isSameAs(mockModel);
        assertThat(cachedModel.getCache()).isSameAs(mockCache);
    }

    @Test
    public void should_not_doublewrap_already_cached_model() {
        // given
        EmbeddingModelCache.configureGlobalCache(mockCache);

        // Create an already cached model
        CachedEmbeddingModel alreadyCachedModel = (CachedEmbeddingModel) CachedEmbeddingModelBuilder.builder()
                .delegate(mockModel)
                .cache(mock(EmbeddingCache.class))
                .build();

        // when
        EmbeddingModel wrappedModel = EmbeddingModelCache.wrap(alreadyCachedModel);

        // then
        assertThat(wrappedModel).isSameAs(alreadyCachedModel);
    }

    @Test
    public void should_configure_redis_cache_correctly() {
        // given
        EmbeddingModelCache.configureGlobalRedisCache("localhost", 6379);

        // when
        EmbeddingModel wrappedModel = EmbeddingModelCache.wrap(mockModel);

        // then
        assertThat(wrappedModel).isInstanceOf(CachedEmbeddingModel.class);
        CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) wrappedModel;
        assertThat(cachedModel.getCache()).isInstanceOf(RedisEmbeddingCache.class);
    }
}
