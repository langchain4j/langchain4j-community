package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.cache.EmbeddingCache;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the EmbeddingCacheTestingSupport utility.
 */
class EmbeddingCacheTestingSupportTest {

    @Mock
    private EmbeddingModel mockModel;

    @Mock
    private EmbeddingCache mockCache;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        EmbeddingModelCache.disableGlobalCache();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        EmbeddingModelCache.disableGlobalCache();
    }

    @Test
    void should_wrap_model_in_record_mode() {
        // given
        String testContextId = "test-123";

        // when
        EmbeddingModel recordingModel = EmbeddingCacheTestingSupport.recordMode(mockModel, testContextId);

        // then
        assertThat(recordingModel).isInstanceOf(CachedEmbeddingModel.class);
        CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) recordingModel;
        assertThat(cachedModel.getCache()).isInstanceOf(TestingEmbeddingCache.class);

        TestingEmbeddingCache testCache = (TestingEmbeddingCache) cachedModel.getCache();
        assertThat(testCache.getMode()).isEqualTo(TestingEmbeddingCache.Mode.RECORD);
        assertThat(testCache.getTestContextId()).isEqualTo(testContextId);
    }

    @Test
    void should_wrap_cached_model_in_record_mode() {
        // given
        String testContextId = "test-123";
        CachedEmbeddingModel alreadyCachedModel = CachedEmbeddingModel.builder()
                .delegate(mockModel)
                .cache(mockCache)
                .build();

        // when
        EmbeddingModel recordingModel = EmbeddingCacheTestingSupport.recordMode(alreadyCachedModel, testContextId);

        // then
        assertThat(recordingModel).isInstanceOf(CachedEmbeddingModel.class);
        CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) recordingModel;
        assertThat(cachedModel.getCache()).isInstanceOf(TestingEmbeddingCache.class);

        TestingEmbeddingCache testCache = (TestingEmbeddingCache) cachedModel.getCache();
        assertThat(testCache.getMode()).isEqualTo(TestingEmbeddingCache.Mode.RECORD);
        assertThat(testCache.getTestContextId()).isEqualTo(testContextId);

        // Should keep the same delegate
        assertThat(cachedModel.getDelegate()).isSameAs(mockModel);
    }

    @Test
    void should_wrap_model_in_play_mode() {
        // given
        String testContextId = "test-123";

        // when
        EmbeddingModel playbackModel = EmbeddingCacheTestingSupport.playMode(mockModel, testContextId);

        // then
        assertThat(playbackModel).isInstanceOf(CachedEmbeddingModel.class);
        CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) playbackModel;
        assertThat(cachedModel.getCache()).isInstanceOf(TestingEmbeddingCache.class);

        TestingEmbeddingCache testCache = (TestingEmbeddingCache) cachedModel.getCache();
        assertThat(testCache.getMode()).isEqualTo(TestingEmbeddingCache.Mode.PLAY);
        assertThat(testCache.getTestContextId()).isEqualTo(testContextId);
    }

    @Test
    void should_configure_global_playback_cache() {
        // given
        String testContextId = "test-123";

        // when
        EmbeddingCacheTestingSupport.configureGlobalPlayMode("localhost", 6379, testContextId);

        // then
        assertThat(EmbeddingModelCache.isGlobalCachingEnabled()).isTrue();

        // Check that if we wrap a model, it gets the test cache
        EmbeddingModel wrappedModel = EmbeddingModelCache.wrap(mockModel);
        assertThat(wrappedModel).isInstanceOf(CachedEmbeddingModel.class);

        CachedEmbeddingModel cachedModel = (CachedEmbeddingModel) wrappedModel;
        assertThat(cachedModel.getCache()).isInstanceOf(TestingEmbeddingCache.class);

        TestingEmbeddingCache testCache = (TestingEmbeddingCache) cachedModel.getCache();
        assertThat(testCache.getMode()).isEqualTo(TestingEmbeddingCache.Mode.PLAY);
        assertThat(testCache.getTestContextId()).isEqualTo(testContextId);
    }
}
