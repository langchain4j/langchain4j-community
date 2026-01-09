package dev.langchain4j.community.store.embedding.s3;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DataType;
import software.amazon.awssdk.services.s3vectors.model.DeleteIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;
import software.amazon.awssdk.services.s3vectors.model.GetIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.NotFoundException;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

/**
 * EmbeddingStore using Amazon S3 Vectors as the backend.
 * Supports cosine and euclidean distance metrics.
 *
 * <p>Note: S3 Vectors limits search results to 100 per query (topK range: 1-100).
 *
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors.html">S3 Vectors Documentation</a>
 */
public class S3VectorsEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3VectorsEmbeddingStore.class);

    /** Default metadata key, aligned with Python langchain-aws. */
    public static final String DEFAULT_TEXT_METADATA_KEY = "_page_content";

    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int SECONDS_TO_WAIT_FOR_INDEX = 30;

    private static final int MAX_TOP_K = 100;

    private final S3VectorsClient s3VectorsClient;
    private final String vectorBucketName;
    private final String indexName;
    private final DistanceMetric distanceMetric;
    private final boolean createIndexIfNotExists;
    private final String textMetadataKey;

    /**
     * Creates a new S3VectorsEmbeddingStore.
     *
     * @param builder the builder containing configuration
     */
    public S3VectorsEmbeddingStore(Builder builder) {
        this.vectorBucketName = ensureNotNull(builder.vectorBucketName, "vectorBucketName");
        this.indexName = ensureNotNull(builder.indexName, "indexName");
        this.distanceMetric = getOrDefault(builder.distanceMetric, DistanceMetric.COSINE);
        this.createIndexIfNotExists = getOrDefault(builder.createIndexIfNotExists, true);
        this.textMetadataKey = getOrDefault(builder.textMetadataKey, DEFAULT_TEXT_METADATA_KEY);
        this.s3VectorsClient = isNull(builder.s3VectorsClient) ? createClient(builder) : builder.s3VectorsClient;
    }

    private S3VectorsClient createClient(Builder builder) {
        Region region = isNull(builder.region) ? DEFAULT_REGION : Region.of(builder.region);

        AwsCredentialsProvider credentialsProvider =
                getOrDefault(builder.credentialsProvider, DefaultCredentialsProvider.create());

        Duration timeout = getOrDefault(builder.timeout, DEFAULT_TIMEOUT);

        return S3VectorsClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(config -> {
                    config.apiCallTimeout(timeout);
                })
                .build();
    }

    /**
     * Creates a new builder for {@link S3VectorsEmbeddingStore}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link S3VectorsEmbeddingStore}.
     *
     * <p>Example usage:
     * <pre>{@code
     * S3VectorsEmbeddingStore store = S3VectorsEmbeddingStore.builder()
     *     .vectorBucketName("my-vector-bucket")
     *     .indexName("my-index")
     *     .region("us-west-2")
     *     .build();
     * }</pre>
     */
    public static class Builder {

        /** Creates a new Builder instance. */
        public Builder() {}

        private S3VectorsClient s3VectorsClient;
        private String vectorBucketName;
        private String indexName;
        private String region;
        private AwsCredentialsProvider credentialsProvider;
        private Duration timeout;
        private DistanceMetric distanceMetric;
        private Boolean createIndexIfNotExists;
        private String textMetadataKey;

        /**
         * Sets a pre-configured S3VectorsClient. If not set, one will be created.
         *
         * @param s3VectorsClient the S3 Vectors client
         * @return this builder
         */
        public Builder s3VectorsClient(S3VectorsClient s3VectorsClient) {
            this.s3VectorsClient = s3VectorsClient;
            return this;
        }

        /**
         * Sets the S3 Vectors bucket name. Required.
         *
         * @param vectorBucketName the bucket name
         * @return this builder
         */
        public Builder vectorBucketName(String vectorBucketName) {
            this.vectorBucketName = vectorBucketName;
            return this;
        }

        /**
         * Sets the index name within the bucket. Required.
         *
         * @param indexName the index name
         * @return this builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * Sets the AWS region. Defaults to us-east-1.
         *
         * @param region the AWS region
         * @return this builder
         */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the AWS credentials provider. Defaults to DefaultCredentialsProvider.
         *
         * @param credentialsProvider the credentials provider
         * @return this builder
         */
        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        /**
         * Sets the API call timeout. Defaults to 30 seconds.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the distance metric for similarity search. Defaults to COSINE.
         *
         * @param distanceMetric the distance metric
         * @return this builder
         */
        public Builder distanceMetric(DistanceMetric distanceMetric) {
            this.distanceMetric = distanceMetric;
            return this;
        }

        /**
         * Sets whether to create the index if it doesn't exist. Defaults to true.
         *
         * @param createIndexIfNotExists true to create index automatically
         * @return this builder
         */
        public Builder createIndexIfNotExists(Boolean createIndexIfNotExists) {
            this.createIndexIfNotExists = createIndexIfNotExists;
            return this;
        }

        /**
         * Sets the metadata key for text content. Defaults to "_page_content".
         *
         * @param textMetadataKey the metadata key
         * @return this builder
         */
        public Builder textMetadataKey(String textMetadataKey) {
            this.textMetadataKey = textMetadataKey;
            return this;
        }

        /**
         * Builds the S3VectorsEmbeddingStore instance.
         *
         * @return a new S3VectorsEmbeddingStore
         */
        public S3VectorsEmbeddingStore build() {
            return new S3VectorsEmbeddingStore(this);
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, textSegments);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        ensureNotEmpty(ids, "ids");
        ensureNotEmpty(embeddings, "embeddings");
        ensureTrue(ids.size() == embeddings.size(), "ids and embeddings must have the same size");
        ensureTrue(
                textSegments == null || textSegments.size() == embeddings.size(),
                "textSegments and embeddings must have the same size");

        if (createIndexIfNotExists && !indexExists()) {
            createIndex(embeddings.get(0).dimension());
        }

        List<PutInputVector> vectors = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = textSegments != null ? textSegments.get(i) : null;

            Document metadata = buildMetadata(textSegment);

            PutInputVector vector = PutInputVector.builder()
                    .key(id)
                    .data(VectorData.builder()
                            .float32(toFloatList(embedding.vector()))
                            .build())
                    .metadata(metadata)
                    .build();

            vectors.add(vector);
        }

        PutVectorsRequest request = PutVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(indexName)
                .vectors(vectors)
                .build();

        s3VectorsClient.putVectors(request);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(
                Collections.singletonList(id),
                Collections.singletonList(embedding),
                textSegment == null ? null : Collections.singletonList(textSegment));
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (request.maxResults() > MAX_TOP_K) {
            log.warn(
                    "S3 Vectors limits maxResults to {}. Requested: {}. Results will be capped.",
                    MAX_TOP_K,
                    request.maxResults());
        }
        int topK = Math.max(1, Math.min(request.maxResults(), MAX_TOP_K));
        QueryVectorsRequest.Builder queryBuilder = QueryVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(indexName)
                .topK(topK)
                .queryVector(VectorData.builder()
                        .float32(toFloatList(request.queryEmbedding().vector()))
                        .build())
                .returnMetadata(true)
                .returnDistance(true);

        if (request.filter() != null) {
            queryBuilder.filter(S3VectorsMetadataFilterMapper.map(request.filter()));
        }

        QueryVectorsResponse response;
        try {
            response = s3VectorsClient.queryVectors(queryBuilder.build());
        } catch (NotFoundException e) {
            // Index doesn't exist yet (created lazily on first add)
            return new EmbeddingSearchResult<>(Collections.emptyList());
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        if (response.vectors() != null) {
            for (QueryOutputVector vectorResult : response.vectors()) {
                double score = distanceToScore(vectorResult.distance());

                if (score >= request.minScore()) {
                    TextSegment textSegment = extractTextSegment(vectorResult.metadata());

                    matches.add(new EmbeddingMatch<>(score, vectorResult.key(), null, textSegment));
                }
            }
        }

        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        DeleteVectorsRequest request = DeleteVectorsRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(indexName)
                .keys(new ArrayList<>(ids))
                .build();

        try {
            s3VectorsClient.deleteVectors(request);
        } catch (NotFoundException e) {
            // Index doesn't exist yet - nothing to delete
        }
    }

    @Override
    public void removeAll(Filter filter) {
        throw new UnsupportedOperationException("removeAll(Filter) is not supported by S3 Vectors");
    }

    @Override
    public void removeAll() {
        try {
            DeleteIndexRequest request = DeleteIndexRequest.builder()
                    .vectorBucketName(vectorBucketName)
                    .indexName(indexName)
                    .build();
            s3VectorsClient.deleteIndex(request);
        } catch (NotFoundException e) {
            // Index doesn't exist - expected for new stores
        }
    }

    private boolean indexExists() {
        try {
            GetIndexRequest request = GetIndexRequest.builder()
                    .vectorBucketName(vectorBucketName)
                    .indexName(indexName)
                    .build();
            s3VectorsClient.getIndex(request);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private void createIndex(int dimension) {
        CreateIndexRequest request = CreateIndexRequest.builder()
                .vectorBucketName(vectorBucketName)
                .indexName(indexName)
                .dataType(DataType.FLOAT32)
                .dimension(dimension)
                .distanceMetric(distanceMetric)
                .build();

        s3VectorsClient.createIndex(request);
        waitForIndexReady();
    }

    private void waitForIndexReady() {
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.SECONDS.toNanos(SECONDS_TO_WAIT_FOR_INDEX);

        while (System.nanoTime() - startTime < timeoutNanos) {
            try {
                GetIndexRequest request = GetIndexRequest.builder()
                        .vectorBucketName(vectorBucketName)
                        .indexName(indexName)
                        .build();
                s3VectorsClient.getIndex(request);
                return;
            } catch (NotFoundException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        log.warn("Index {} was not ready within {} seconds", indexName, SECONDS_TO_WAIT_FOR_INDEX);
    }

    private Document buildMetadata(TextSegment textSegment) {
        if (textSegment == null) {
            return Document.fromMap(new LinkedHashMap<>());
        }

        Map<String, Document> metadataMap = new LinkedHashMap<>();
        metadataMap.put(textMetadataKey, Document.fromString(textSegment.text()));

        if (textSegment.metadata() != null) {
            textSegment.metadata().toMap().forEach((key, value) -> {
                if (value != null) {
                    metadataMap.put(key, toDocument(value));
                }
            });
        }

        return Document.fromMap(metadataMap);
    }

    private Document toDocument(Object value) {
        if (value instanceof String) {
            return Document.fromString((String) value);
        } else if (value instanceof Number) {
            return Document.fromNumber(((Number) value).toString());
        } else if (value instanceof Boolean) {
            return Document.fromBoolean((Boolean) value);
        } else {
            return Document.fromString(String.valueOf(value));
        }
    }

    private TextSegment extractTextSegment(Document metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }

        Map<String, Document> metadataMap = metadata.asMap();
        if (metadataMap == null || metadataMap.isEmpty()) {
            return null;
        }

        String text = null;
        Map<String, Object> extractedMetadata = new HashMap<>();

        for (Map.Entry<String, Document> entry : metadataMap.entrySet()) {
            String key = entry.getKey();
            Document value = entry.getValue();

            if (textMetadataKey.equals(key)) {
                text = value.asString();
            } else {
                extractedMetadata.put(key, extractValue(value));
            }
        }

        if (text == null) {
            return null;
        }

        return TextSegment.from(text, extractedMetadata.isEmpty() ? new Metadata() : new Metadata(extractedMetadata));
    }

    private Object extractValue(Document document) {
        if (document.isString()) {
            return document.asString();
        } else if (document.isNumber()) {
            return Double.parseDouble(document.asNumber().toString());
        } else if (document.isBoolean()) {
            return document.asBoolean();
        }
        return null;
    }

    // Manual loop avoids boxing overhead from streams
    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private double distanceToScore(Float distance) {
        if (distance == null) {
            return 0.0;
        }
        if (distanceMetric == DistanceMetric.COSINE) {
            return RelevanceScore.fromCosineSimilarity(1.0 - distance);
        }
        // Euclidean
        return 1.0 / (1.0 + distance);
    }

    @Override
    public void close() {
        if (s3VectorsClient != null) {
            s3VectorsClient.close();
        }
    }

    S3VectorsClient getS3VectorsClient() {
        return s3VectorsClient;
    }

    String getVectorBucketName() {
        return vectorBucketName;
    }

    String getIndexName() {
        return indexName;
    }
}
