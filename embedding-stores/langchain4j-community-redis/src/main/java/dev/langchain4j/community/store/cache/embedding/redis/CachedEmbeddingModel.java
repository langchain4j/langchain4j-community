package dev.langchain4j.community.store.cache.embedding.redis;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper for EmbeddingModel that caches embedding results in Redis.
 * <p>
 * This model intercepts embedding requests, checks the cache first, and only
 * calls the underlying model when necessary, caching the results for future use.
 */
public class CachedEmbeddingModel implements EmbeddingModel {

    private static final Logger logger = LoggerFactory.getLogger(CachedEmbeddingModel.class);

    private final EmbeddingModel delegate;
    private final EmbeddingCache cache;

    /**
     * Creates a new cached embedding model.
     *
     * @param delegate The underlying embedding model to delegate to when cache misses occur
     * @param cache    The cache to use for storing and retrieving embeddings
     */
    public CachedEmbeddingModel(EmbeddingModel delegate, EmbeddingCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    /**
     * Embeds a single text segment, using the cache if available.
     *
     * @param textSegment The text segment to embed
     * @return The embedding response
     */
    public Response<Embedding> embed(TextSegment textSegment) {
        String text = textSegment.text();

        // Check cache first
        Optional<Embedding> cachedEmbedding = cache.get(text);

        if (cachedEmbedding.isPresent()) {
            logger.debug("Cache hit for text: {}", text);
            return Response.from(cachedEmbedding.get());
        }

        logger.debug("Cache miss for text: {}", text);
        // Cache miss, get from delegate and store in cache
        Response<Embedding> delegateResponse = delegate.embed(textSegment);
        Embedding embedding = delegateResponse.content();

        // Store in cache for future use
        cache.put(text, embedding);

        return delegateResponse;
    }

    /**
     * Implements the EmbeddingModel interface method to embed a string.
     *
     * @param text The text to embed
     * @return A Response containing the embedding
     */
    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>(textSegments.size());
        List<Integer> cacheMissIndices = new ArrayList<>();
        List<TextSegment> cacheMissSegments = new ArrayList<>();

        // Check cache for each segment
        for (int i = 0; i < textSegments.size(); i++) {
            TextSegment segment = textSegments.get(i);
            String text = segment.text();

            Optional<Embedding> cachedEmbedding = cache.get(text);
            if (cachedEmbedding.isPresent()) {
                logger.debug("Cache hit for text segment {}: {}", i, text);
                embeddings.add(cachedEmbedding.get());
            } else {
                logger.debug("Cache miss for text segment {}: {}", i, text);
                // Add placeholder to maintain order
                embeddings.add(null);
                cacheMissIndices.add(i);
                cacheMissSegments.add(segment);
            }
        }

        // If we had any cache misses, delegate to the underlying model
        if (!cacheMissSegments.isEmpty()) {
            Response<List<Embedding>> delegateResponse = delegate.embedAll(cacheMissSegments);
            List<Embedding> delegateEmbeddings = delegateResponse.content();

            // Update cache and fill in the embeddings
            for (int i = 0; i < cacheMissIndices.size(); i++) {
                int originalIndex = cacheMissIndices.get(i);
                Embedding embedding = delegateEmbeddings.get(i);
                String text = textSegments.get(originalIndex).text();

                // Store in cache
                cache.put(text, embedding);

                // Update our result list
                embeddings.set(originalIndex, embedding);
            }

            // Return with token usage from the delegate
            return Response.from(embeddings, delegateResponse.tokenUsage());
        }

        // If all were cache hits, just return the embeddings with no token usage info
        return Response.from(embeddings);
    }

    /**
     * Clears all entries from the embedding cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Gets the cache instance used by this model.
     *
     * @return The EmbeddingCache instance
     */
    public EmbeddingCache getCache() {
        return cache;
    }

    /**
     * Gets the underlying delegate embedding model.
     *
     * @return The underlying embedding model
     */
    public EmbeddingModel getDelegate() {
        return delegate;
    }
}
