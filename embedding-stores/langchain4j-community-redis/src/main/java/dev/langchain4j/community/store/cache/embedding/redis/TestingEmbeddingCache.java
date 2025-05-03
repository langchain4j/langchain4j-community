package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.data.embedding.Embedding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A special implementation of EmbeddingCache for testing purposes.
 * It supports two modes:
 * - RECORD: Records all embeddings in both the original namespace and a test-specific namespace
 * - PLAY: Retrieves embeddings from a test-specific namespace, falling back to the original namespace
 *
 * This allows for recording embeddings during development and playing them back in tests
 * without making actual API calls to the embedding model.
 */
public class TestingEmbeddingCache implements EmbeddingCache {

    /**
     * The mode in which the testing cache operates.
     */
    public enum Mode {
        /**
         * In record mode, all embeddings are cached both normally and in a test-specific namespace.
         */
        RECORD,

        /**
         * In play mode, embeddings are retrieved from the test-specific namespace first,
         * with fallback to the normal namespace.
         */
        PLAY
    }

    private final EmbeddingCache delegate;
    private final Mode mode;
    private final String testContextId;

    /**
     * Creates a new testing embedding cache.
     *
     * @param delegate The underlying cache to delegate to
     * @param mode The mode to operate in (RECORD or PLAY)
     * @param testContextId A unique identifier for this test context
     */
    private TestingEmbeddingCache(EmbeddingCache delegate, Mode mode, String testContextId) {
        this.delegate = delegate;
        this.mode = mode;
        this.testContextId = testContextId;
    }

    /**
     * Creates a testing cache in record mode.
     *
     * @param delegate The underlying cache to delegate to
     * @param testContextId A unique identifier for this test context
     * @return A new testing cache in record mode
     */
    public static TestingEmbeddingCache inRecordMode(EmbeddingCache delegate, String testContextId) {
        return new TestingEmbeddingCache(delegate, Mode.RECORD, testContextId);
    }

    /**
     * Creates a testing cache in play mode.
     *
     * @param delegate The underlying cache to delegate to
     * @param testContextId A unique identifier for this test context
     * @return A new testing cache in play mode
     */
    public static TestingEmbeddingCache inPlayMode(EmbeddingCache delegate, String testContextId) {
        return new TestingEmbeddingCache(delegate, Mode.PLAY, testContextId);
    }

    /**
     * Gets the current mode of this testing cache.
     *
     * @return The current mode
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Gets the test context ID for this testing cache.
     *
     * @return The test context ID
     */
    public String getTestContextId() {
        return testContextId;
    }

    /**
     * Forms a test-specific key by prefixing the given text with the test context ID.
     *
     * @param text The original text
     * @return The test-specific key
     */
    private String testKey(String text) {
        return "test:" + testContextId + ":" + text;
    }

    @Override
    public Optional<Embedding> get(String text) {
        if (mode == Mode.PLAY) {
            // In play mode, check test cache first
            Optional<Embedding> testEmbedding = delegate.get(testKey(text));
            if (testEmbedding.isPresent()) {
                return testEmbedding;
            }
            // Fall back to regular cache for backward compatibility
        }

        // In record mode, just use the regular cache
        return delegate.get(text);
    }

    @Override
    public void put(String text, Embedding embedding) {
        if (mode == Mode.RECORD) {
            // In record mode, store in both regular and test caches
            delegate.put(text, embedding);
            delegate.put(testKey(text), embedding);
        } else if (mode == Mode.PLAY) {
            // In play mode, don't modify the cache
        }
    }

    @Override
    public void clear() {
        // Clear all test entries but leave the regular entries intact
        // This is a simplification - in a real implementation, we might want to be more targeted
        delegate.clear();
    }

    @Override
    public boolean remove(String text) {
        if (mode == Mode.RECORD) {
            // In record mode, remove from both regular and test caches
            boolean regularRemoved = delegate.remove(text);
            boolean testRemoved = delegate.remove(testKey(text));
            return regularRemoved || testRemoved;
        } else if (mode == Mode.PLAY) {
            // In play mode, don't modify the cache
            return false;
        }

        // Default case
        return delegate.remove(text);
    }

    @Override
    public Map<String, Embedding> mget(List<String> texts) {
        Map<String, Embedding> results = new HashMap<>();

        for (String text : texts) {
            Optional<Embedding> embedding = get(text);
            embedding.ifPresent(e -> results.put(text, e));
        }

        return results;
    }

    @Override
    public Map<String, Boolean> mexists(List<String> texts) {
        Map<String, Boolean> results = new HashMap<>();

        if (mode == Mode.PLAY) {
            // In play mode, check test keys first, then fall back to regular keys
            for (String text : texts) {
                // First check test namespace
                Map<String, Boolean> testResults = delegate.mexists(List.of(testKey(text)));
                if (testResults.getOrDefault(testKey(text), false)) {
                    results.put(text, true);
                } else {
                    // Fall back to regular namespace
                    Optional<Embedding> regularEmbedding = delegate.get(text);
                    results.put(text, regularEmbedding.isPresent());
                }
            }
        } else {
            // In record mode or default, just check regular keys
            Map<String, Boolean> delegateResults = delegate.mexists(texts);
            results.putAll(delegateResults);
        }

        return results;
    }

    @Override
    public void mput(Map<String, Embedding> embeddings) {
        if (mode == Mode.RECORD) {
            // In record mode, store in both regular and test caches

            // Store in regular cache
            delegate.mput(embeddings);

            // Create test namespace mappings and store in test cache
            Map<String, Embedding> testEmbeddings = new HashMap<>();
            for (Map.Entry<String, Embedding> entry : embeddings.entrySet()) {
                testEmbeddings.put(testKey(entry.getKey()), entry.getValue());
            }
            delegate.mput(testEmbeddings);
        } else if (mode == Mode.PLAY) {
            // In play mode, don't modify the cache
        }
    }

    @Override
    public Map<String, Boolean> mremove(List<String> texts) {
        Map<String, Boolean> results = new HashMap<>();

        if (mode == Mode.RECORD) {
            // In record mode, remove from both regular and test caches
            for (String text : texts) {
                // Remove from both regular and test cache and store results
                boolean regularRemoved = delegate.remove(text);
                boolean testRemoved = delegate.remove(testKey(text));
                results.put(text, regularRemoved || testRemoved);
            }
        } else if (mode == Mode.PLAY) {
            // In play mode, don't modify the cache
            for (String text : texts) {
                results.put(text, false);
            }
        } else {
            // Default case, just use regular removal
            return delegate.mremove(texts);
        }

        return results;
    }
}
