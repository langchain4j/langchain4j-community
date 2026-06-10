package dev.langchain4j.community.store.embedding.hazelcast;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import com.hazelcast.config.vector.Metric;
import com.hazelcast.config.vector.VectorCollectionConfig;
import com.hazelcast.config.vector.VectorIndexConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.vector.SearchOptions;
import com.hazelcast.vector.SearchResult;
import com.hazelcast.vector.SearchResults;
import com.hazelcast.vector.VectorCollection;
import com.hazelcast.vector.VectorDocument;
import com.hazelcast.vector.VectorValues;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * An {@link EmbeddingStore} backed by a Hazelcast Enterprise {@link VectorCollection}.
 *
 * <p>Each entry in the {@link VectorCollection} consists of a {@link String} key (the embedding ID)
 * and a {@link VectorDocument} whose value is a {@link TextSegmentDocument} holding the text and
 * metadata, and whose vectors are the embedding float array.
 *
 * <p><strong>Requirements:</strong> This store depends on <strong>Hazelcast Enterprise</strong>
 * ({@code com.hazelcast:hazelcast-enterprise}), which <strong>require the Hazelcast Enterprise
 * repository</strong>.
 * To build or run against it you must:
 * <ul>
 *   <li>add the Hazelcast Enterprise repository ({@code https://repository.hazelcast.com/release/})
 *       to your {@code ~/.m2/settings.xml} or {@code pom.xml}; and</li>
 *   <li>provide a valid Hazelcast Enterprise license key (e.g. {@code config.setLicenseKey(...)}
 *       or the {@code HZ_LICENSEKEY} environment variable).</li>
 * </ul>
 * The {@link VectorCollection} must be configured with exactly one vector index whose dimension
 * matches the embedding model in use. The metric defaults to {@link Metric#COSINE}.
 *
 * <h2>Minimal embedded-member example</h2>
 * <pre>{@code
 * HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());
 *
 * EmbeddingStore<TextSegment> store = HazelcastEmbeddingStore.builder()
 *         .hazelcastInstance(hz)
 *         .collectionName("embeddings")
 *         .dimension(384)
 *         .build();
 * }</pre>
 *
 * <h2>Example with pre-configured VectorCollection</h2>
 * <pre>{@code
 * VectorCollectionConfig config = new VectorCollectionConfig("embeddings")
 *         .addVectorIndexConfig(new VectorIndexConfig()
 *                 .setDimension(384)
 *                 .setMetric(Metric.COSINE));
 * VectorCollection<String, TextSegmentDocument> collection =
 *         VectorCollection.getCollection(hz, config);
 *
 * EmbeddingStore<TextSegment> store = HazelcastEmbeddingStore.create(collection);
 * }</pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>{@link #removeAll(Filter)} is not supported — Hazelcast {@link VectorCollection} does not
 *       provide a server-side predicate delete. An {@link UnsupportedFeatureException} is thrown.</li>
 *   <li>{@link EmbeddingSearchRequest#filter()} (metadata filtering during search) is not supported
 *       server-side. If a filter is present in the request, a warning is logged and the filter is
 *       applied client-side after retrieval, which may return fewer results than {@code maxResults}.</li>
 * </ul>
 */
public class HazelcastEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = Logger.getLogger(HazelcastEmbeddingStore.class.getName());

    /**
     * The default {@link VectorCollection} name.
     */
    public static final String DEFAULT_COLLECTION_NAME = "embeddings";

    /**
     * The {@link VectorCollection} used to store the embeddings.
     */
    protected final VectorCollection<String, TextSegmentDocument> collection;

    /**
     * Creates a {@link HazelcastEmbeddingStore}.
     *
     * @param collection the {@link VectorCollection} to store embeddings
     */
    protected HazelcastEmbeddingStore(VectorCollection<String, TextSegmentDocument> collection) {
        this.collection = ensureNotNull(collection, "collection");
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        ensureNotBlank(id, "id");
        ensureNotNull(embedding, "embedding");
        collection
                .setAsync(id, toDocument(embedding, null))
                .toCompletableFuture()
                .join();
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        String id = randomUUID();
        ensureNotNull(embedding, "embedding");
        collection
                .setAsync(id, toDocument(embedding, segment))
                .toCompletableFuture()
                .join();
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Skipped adding empty embeddings");
            return;
        }

        boolean hasSegments = segments != null && !segments.isEmpty();
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        if (hasSegments) {
            ensureTrue(embeddings.size() == segments.size(), "embeddings size is not equal to segments size");
        }

        Map<String, VectorDocument<TextSegmentDocument>> batch = new HashMap<>();
        for (int i = 0; i < embeddings.size(); i++) {
            TextSegment segment = hasSegments ? segments.get(i) : null;
            batch.put(ids.get(i), toDocument(embeddings.get(i), segment));
        }
        collection.putAllAsync(batch).toCompletableFuture().join();
    }

    @Override
    public void remove(String id) {
        ensureNotBlank(id, "id");
        collection.deleteAsync(id).toCompletableFuture().join();
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String id : ids) {
            futures.add(collection.deleteAsync(id).toCompletableFuture());
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Not supported. {@link VectorCollection} does not provide a server-side predicate delete.
     *
     * @throws UnsupportedFeatureException always
     */
    @Override
    public void removeAll(Filter filter) {
        throw new UnsupportedFeatureException("removeAll(Filter) is not supported by HazelcastEmbeddingStore. "
                + "Hazelcast VectorCollection does not provide a server-side predicate delete.");
    }

    /**
     * Removes all embeddings from the underlying {@link VectorCollection}.
     * <p>
     * Only the entries are cleared; the collection and its index configuration are preserved,
     * so this store remains usable afterwards.
     */
    @Override
    public void removeAll() {
        collection.clearAsync().toCompletableFuture().join();
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        ensureNotNull(request, "request");

        if (request.filter() != null) {
            log.warning("HazelcastEmbeddingStore: EmbeddingSearchRequest.filter() is not supported "
                    + "server-side by Hazelcast VectorCollection. The filter will be applied "
                    + "client-side after retrieval, which may return fewer than maxResults matches.");
        }

        SearchOptions options = SearchOptions.builder()
                .limit(request.maxResults())
                .includeValue()
                .includeVectors()
                .build();

        SearchResults<String, TextSegmentDocument> results;
        try {
            results = collection
                    .searchAsync(VectorValues.of(request.queryEmbedding().vector()), options)
                    .toCompletableFuture()
                    .join();
        } catch (Exception e) {
            throw new RuntimeException("Hazelcast vector search failed", e);
        }

        double minScore = request.minScore();
        Filter filter = request.filter();

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        Iterator<? extends SearchResult<String, TextSegmentDocument>> it = results.results();
        while (it.hasNext()) {
            SearchResult<String, TextSegmentDocument> result = it.next();

            // Hazelcast already returns a non-negative, normalized similarity in [0, 1]
            // (cosine "adjusted to be non-negative"), so it is the relevance score directly.
            double score = result.getScore();
            if (score < minScore) {
                continue;
            }

            TextSegmentDocument doc = result.getValue();
            TextSegment segment = doc != null ? doc.toTextSegment() : null;

            // client-side metadata filter (best-effort when filter is set)
            if (filter != null && segment != null) {
                if (!filter.test(segment.metadata())) {
                    continue;
                }
            }

            VectorValues vectors = result.getVectors();
            Embedding embedding = vectors instanceof VectorValues.SingleVectorValues singleVectors
                    ? new Embedding(singleVectors.vector())
                    : null;

            matches.add(new EmbeddingMatch<>(score, result.getKey(), embedding, segment));
        }

        return new EmbeddingSearchResult<>(matches);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private VectorDocument<TextSegmentDocument> toDocument(Embedding embedding, TextSegment segment) {
        return VectorDocument.of(TextSegmentDocument.from(segment), VectorValues.of(embedding.vector()));
    }

    // -------------------------------------------------------------------------
    // Static factories
    // -------------------------------------------------------------------------

    /**
     * Create a {@link HazelcastEmbeddingStore} that uses a pre-configured
     * {@link VectorCollection} directly.
     *
     * @param collection the {@link VectorCollection}; must not be {@code null}
     * @return a {@link HazelcastEmbeddingStore}
     */
    public static HazelcastEmbeddingStore create(VectorCollection<String, TextSegmentDocument> collection) {
        return new HazelcastEmbeddingStore(collection);
    }

    /**
     * Return a {@link Builder} to build a {@link HazelcastEmbeddingStore}.
     *
     * @return a {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * A builder to create {@link HazelcastEmbeddingStore} instances.
     */
    public static class Builder {

        private HazelcastInstance hazelcastInstance;
        private String collectionName = DEFAULT_COLLECTION_NAME;
        private int dimension;
        private Metric metric = Metric.COSINE;

        protected Builder() {}

        /**
         * Set the {@link HazelcastInstance}. Required unless a pre-configured
         * {@link VectorCollection} is supplied via {@link #create(VectorCollection)}.
         *
         * @param hazelcastInstance the instance; must not be {@code null}
         * @return this builder
         */
        public Builder hazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance;
            return this;
        }

        /**
         * Set the {@link VectorCollection} name.
         * Defaults to {@value HazelcastEmbeddingStore#DEFAULT_COLLECTION_NAME}.
         *
         * @param collectionName the collection name
         * @return this builder
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = isNullOrBlank(collectionName) ? DEFAULT_COLLECTION_NAME : collectionName;
            return this;
        }

        /**
         * Set the vector dimension. Must match the embedding model in use.
         *
         * @param dimension the number of dimensions; must be positive
         * @return this builder
         */
        public Builder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * Set the similarity metric used by the vector index.
         * Defaults to {@link Metric#COSINE}.
         *
         * @param metric the metric; must not be {@code null}
         * @return this builder
         */
        public Builder metric(Metric metric) {
            this.metric = ensureNotNull(metric, "metric");
            return this;
        }

        /**
         * Builds the {@link HazelcastEmbeddingStore}.
         *
         * @return a new store
         * @throws IllegalArgumentException if {@code hazelcastInstance} or {@code dimension}
         *                                  were not set
         */
        public HazelcastEmbeddingStore build() {
            ensureNotNull(hazelcastInstance, "hazelcastInstance");
            ensureTrue(dimension > 0, "dimension must be a positive integer");

            VectorCollectionConfig config = new VectorCollectionConfig(collectionName)
                    .addVectorIndexConfig(
                            new VectorIndexConfig().setDimension(dimension).setMetric(metric));

            VectorCollection<String, TextSegmentDocument> col =
                    VectorCollection.getCollection(hazelcastInstance, config);

            return new HazelcastEmbeddingStore(col);
        }
    }
}
