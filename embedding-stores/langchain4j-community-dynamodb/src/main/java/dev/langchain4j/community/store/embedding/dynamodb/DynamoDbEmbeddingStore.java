package dev.langchain4j.community.store.embedding.dynamodb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.SearchResultItem;
import software.amazon.awssdk.services.dynamodb.model.SearchSchemaElement;
import software.amazon.awssdk.services.dynamodb.model.SearchSchemaElementType;
import software.amazon.awssdk.services.dynamodb.model.SearchVectorsRequest;
import software.amazon.awssdk.services.dynamodb.model.SearchVectorsResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.VectorAttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;
import software.amazon.awssdk.services.dynamodb.model.VectorIndex;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * An {@link EmbeddingStore} backed by Amazon DynamoDB's native vector search.
 *
 * <p>Each embedding is stored as a single DynamoDB item whose partition key is the embedding id.
 * The embedding vector is stored as a list of numbers under a configurable attribute, over which a
 * DynamoDB <em>vector index</em> performs the similarity search. The associated {@link TextSegment}
 * text is stored under {@link #DEFAULT_TEXT_METADATA_KEY} (configurable) and each metadata entry is
 * stored as its own top-level attribute.
 *
 * <p>DynamoDB vector search supports the {@code COSINE}, {@code EUCLIDEAN} and {@code DOT_PRODUCT}
 * distance functions and vectors up to 4096 dimensions. A single search returns at most 100 results
 * (the {@code TopK} limit), so {@code maxResults} is capped at 100.
 *
 * <h2>Metadata numbers</h2>
 *
 * <p>Integer and long metadata values are stored as native DynamoDB numbers. Float and double values
 * are stored as strings instead, because DynamoDB's Number type cannot represent subnormal magnitudes
 * (smaller than roughly {@code 1E-130}) and would reject values such as {@link Double#MIN_VALUE}. This
 * preserves the exact value and Java type on read-back. Equality filters on float and double metadata
 * work because filter values are encoded with the same scheme (see {@link DynamoDbAttributeCodec}).
 *
 * <p>Metadata keys must not collide with the configured key, vector, or text attribute names
 * (by default {@code id}, {@code embedding} and {@code _page_content}); such a collision throws
 * {@link IllegalArgumentException} on write.
 *
 * <h2>Filtering</h2>
 *
 * <p>DynamoDB vector search can filter results only on attributes declared in the vector index
 * search schema — the {@code HASH} key and {@code INLINE_FILTER} attributes — and only with equality.
 * Those attributes are fixed when the index is created, so to use metadata filters you must declare
 * them up front with {@link Builder#inlineFilterAttributes(List)}. Only {@link dev.langchain4j.store.embedding.filter.comparison.IsEqualTo}
 * filters (optionally combined with {@code AND}) are supported.
 *
 * @see <a href="https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/">Amazon DynamoDB Documentation</a>
 */
public class DynamoDbEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbEmbeddingStore.class);

    /** Default metadata key under which the {@link TextSegment} text is stored. */
    public static final String DEFAULT_TEXT_METADATA_KEY = "_page_content";

    /** Default name of the item attribute holding the embedding vector. */
    public static final String DEFAULT_VECTOR_ATTRIBUTE = "embedding";

    /** Default name of the partition key attribute holding the embedding id. */
    public static final String DEFAULT_KEY_ATTRIBUTE = "id";

    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int SECONDS_TO_WAIT_FOR_TABLE = 60;

    /** DynamoDB BatchWriteItem accepts at most 25 write requests per call. */
    private static final int MAX_BATCH_WRITE_SIZE = 25;

    /** Maximum number of BatchWriteItem calls per chunk before giving up on unprocessed items. */
    private static final int MAX_BATCH_WRITE_ATTEMPTS = 10;

    private static final long BATCH_WRITE_BASE_BACKOFF_MILLIS = 100;
    private static final long MAX_BACKOFF_MILLIS = 5_000;

    /** DynamoDB SearchVectors limits TopK to the range 1..100. */
    private static final int MAX_TOP_K = 100;

    private final DynamoDbClient dynamoDbClient;

    /** True only when this store created the client itself and is therefore responsible for closing it. */
    private final boolean ownsClient;

    private final String tableName;
    private final String indexName;
    private final String keyAttribute;
    private final String vectorAttribute;
    private final String textMetadataKey;
    private final VectorDistanceFunction distanceFunction;
    private final boolean createTableIfNotExists;
    private final List<String> inlineFilterAttributes;

    public DynamoDbEmbeddingStore(Builder builder) {
        this.tableName = ensureNotNull(builder.tableName, "tableName");
        this.indexName = ensureNotNull(builder.indexName, "indexName");
        this.keyAttribute = getOrDefault(builder.keyAttribute, DEFAULT_KEY_ATTRIBUTE);
        this.vectorAttribute = getOrDefault(builder.vectorAttribute, DEFAULT_VECTOR_ATTRIBUTE);
        this.textMetadataKey = getOrDefault(builder.textMetadataKey, DEFAULT_TEXT_METADATA_KEY);
        this.distanceFunction = getOrDefault(builder.distanceFunction, VectorDistanceFunction.COSINE);
        this.createTableIfNotExists = getOrDefault(builder.createTableIfNotExists, true);
        this.inlineFilterAttributes =
                builder.inlineFilterAttributes == null ? List.of() : List.copyOf(builder.inlineFilterAttributes);
        this.ownsClient = isNull(builder.dynamoDbClient);
        this.dynamoDbClient = ownsClient ? createClient(builder) : builder.dynamoDbClient;
    }

    private DynamoDbClient createClient(Builder builder) {
        Region region = isNull(builder.region) ? DEFAULT_REGION : Region.of(builder.region);

        AwsCredentialsProvider credentialsProvider =
                getOrDefault(builder.credentialsProvider, DefaultCredentialsProvider.create());

        Duration timeout = getOrDefault(builder.timeout, DEFAULT_TIMEOUT);

        return DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(config -> config.apiCallTimeout(timeout))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DynamoDbEmbeddingStore}.
     *
     * <p>Example usage:
     * <pre>{@code
     * DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
     *     .tableName("my-embeddings")
     *     .indexName("my-vector-index")
     *     .region("us-east-1")
     *     .build();
     * }</pre>
     */
    public static class Builder {

        public Builder() {}

        private DynamoDbClient dynamoDbClient;
        private String tableName;
        private String indexName;
        private String keyAttribute;
        private String vectorAttribute;
        private String textMetadataKey;
        private String region;
        private AwsCredentialsProvider credentialsProvider;
        private Duration timeout;
        private VectorDistanceFunction distanceFunction;
        private Boolean createTableIfNotExists;
        private List<String> inlineFilterAttributes;

        /**
         * Sets a pre-configured {@link DynamoDbClient}. If not set, one is created from the region,
         * credentials provider and timeout.
         */
        public Builder dynamoDbClient(DynamoDbClient dynamoDbClient) {
            this.dynamoDbClient = dynamoDbClient;
            return this;
        }

        /** Sets the DynamoDB table name. Required. */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /** Sets the vector index name within the table. Required. */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /** Sets the partition key attribute name. Defaults to {@value #DEFAULT_KEY_ATTRIBUTE}. */
        public Builder keyAttribute(String keyAttribute) {
            this.keyAttribute = keyAttribute;
            return this;
        }

        /** Sets the attribute name that holds the embedding vector. Defaults to {@value #DEFAULT_VECTOR_ATTRIBUTE}. */
        public Builder vectorAttribute(String vectorAttribute) {
            this.vectorAttribute = vectorAttribute;
            return this;
        }

        /** Sets the metadata key for the text content. Defaults to {@value #DEFAULT_TEXT_METADATA_KEY}. */
        public Builder textMetadataKey(String textMetadataKey) {
            this.textMetadataKey = textMetadataKey;
            return this;
        }

        /** Sets the AWS region. Defaults to us-east-1. */
        public Builder region(String region) {
            this.region = region;
            return this;
        }

        /** Sets the AWS credentials provider. Defaults to {@link DefaultCredentialsProvider}. */
        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        /** Sets the API call timeout. Defaults to 30 seconds. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Sets the distance function for similarity search. Defaults to COSINE. */
        public Builder distanceFunction(VectorDistanceFunction distanceFunction) {
            this.distanceFunction = distanceFunction;
            return this;
        }

        /** Sets whether to create the table and vector index if they do not exist. Defaults to true. */
        public Builder createTableIfNotExists(Boolean createTableIfNotExists) {
            this.createTableIfNotExists = createTableIfNotExists;
            return this;
        }

        /**
         * Declares the metadata attributes that can be used in filters. These become
         * {@code INLINE_FILTER} elements of the vector index search schema and can therefore only be
         * set when the index is created. Defaults to none (no metadata filtering).
         */
        public Builder inlineFilterAttributes(List<String> inlineFilterAttributes) {
            this.inlineFilterAttributes = inlineFilterAttributes;
            return this;
        }

        public DynamoDbEmbeddingStore build() {
            return new DynamoDbEmbeddingStore(this);
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

        if (createTableIfNotExists && !tableExists()) {
            createTable(embeddings.get(0).dimension());
        }

        List<WriteRequest> writeRequests = new ArrayList<>(embeddings.size());
        for (int i = 0; i < embeddings.size(); i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = textSegments != null ? textSegments.get(i) : null;

            Map<String, AttributeValue> item = buildItem(id, embedding, textSegment);
            writeRequests.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(item).build())
                    .build());
        }

        batchWrite(writeRequests);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(
                Collections.singletonList(id),
                Collections.singletonList(embedding),
                textSegment == null ? null : Collections.singletonList(textSegment));
    }

    private void batchWrite(List<WriteRequest> writeRequests) {
        for (int start = 0; start < writeRequests.size(); start += MAX_BATCH_WRITE_SIZE) {
            int end = Math.min(start + MAX_BATCH_WRITE_SIZE, writeRequests.size());
            List<WriteRequest> chunk = writeRequests.subList(start, end);

            Map<String, List<WriteRequest>> requestItems = new HashMap<>();
            requestItems.put(tableName, new ArrayList<>(chunk));

            // BatchWriteItem may return unprocessed items under load; retry them with exponential
            // backoff, giving up after MAX_BATCH_WRITE_ATTEMPTS so persistent throttling cannot spin
            // forever.
            Map<String, List<WriteRequest>> unprocessed = requestItems;
            for (int attempt = 1; !unprocessed.isEmpty(); attempt++) {
                BatchWriteItemRequest request = BatchWriteItemRequest.builder()
                        .requestItems(unprocessed)
                        .build();
                unprocessed = dynamoDbClient.batchWriteItem(request).unprocessedItems();
                if (unprocessed.isEmpty()) {
                    break;
                }
                if (attempt >= MAX_BATCH_WRITE_ATTEMPTS) {
                    throw new IllegalStateException("DynamoDB BatchWriteItem still had unprocessed items after "
                            + MAX_BATCH_WRITE_ATTEMPTS + " attempts; giving up (likely sustained throttling)");
                }
                sleep(backoffMillis(attempt));
            }
        }
    }

    /** Exponential backoff (100ms, 200ms, 400ms, ...) capped at 5 seconds. */
    private static long backoffMillis(int attempt) {
        return Math.min(BATCH_WRITE_BASE_BACKOFF_MILLIS << (attempt - 1), MAX_BACKOFF_MILLIS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while backing off before a BatchWriteItem retry", e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (request.maxResults() > MAX_TOP_K) {
            log.warn(
                    "DynamoDB vector search limits maxResults to {}. Requested: {}. Results will be capped.",
                    MAX_TOP_K,
                    request.maxResults());
        }
        int topK = Math.max(1, Math.min(request.maxResults(), MAX_TOP_K));

        SearchVectorsRequest.Builder queryBuilder = SearchVectorsRequest.builder()
                .tableName(tableName)
                .indexName(indexName)
                .topK(topK)
                .searchVector(toAttributeValues(request.queryEmbedding().vector()));

        if (request.filter() != null) {
            DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(request.filter());
            queryBuilder
                    .searchConditionExpression(condition.expression)
                    .expressionAttributeNames(condition.expressionAttributeNames)
                    .expressionAttributeValues(condition.expressionAttributeValues);
        }

        SearchVectorsResponse response;
        try {
            response = dynamoDbClient.searchVectors(queryBuilder.build());
        } catch (ResourceNotFoundException e) {
            // Table or index does not exist yet (created lazily on first add)
            return new EmbeddingSearchResult<>(Collections.emptyList());
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (SearchResultItem result : response.searchResults()) {
            double score = distanceToScore(result.score());
            if (score < request.minScore()) {
                continue;
            }

            Map<String, AttributeValue> item = result.item();
            String id = item.containsKey(keyAttribute) ? item.get(keyAttribute).s() : null;
            if (id == null) {
                // The key was not projected into the result: likely a misconfigured keyAttribute or
                // index projection. Surface it rather than silently returning a null-id match.
                log.warn(
                        "DynamoDB search result is missing the key attribute '{}'; returning a match with a null id. "
                                + "Check that keyAttribute matches the table's partition key.",
                        keyAttribute);
            }
            TextSegment textSegment = extractTextSegment(item);

            matches.add(new EmbeddingMatch<>(score, id, null, textSegment));
        }

        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (isNullOrEmpty(ids)) {
            return;
        }

        List<WriteRequest> writeRequests = new ArrayList<>(ids.size());
        for (String id : ids) {
            Map<String, AttributeValue> key = Map.of(keyAttribute, AttributeValue.fromS(id));
            writeRequests.add(WriteRequest.builder()
                    .deleteRequest(DeleteRequest.builder().key(key).build())
                    .build());
        }

        try {
            batchWrite(writeRequests);
        } catch (ResourceNotFoundException e) {
            // Table does not exist yet - nothing to delete
        }
    }

    @Override
    public void removeAll(Filter filter) {
        throw new UnsupportedOperationException("removeAll(Filter) is not supported by DynamoDB vector search");
    }

    @Override
    public void removeAll() {
        List<Map<String, AttributeValue>> keys = scanAllKeys();
        if (keys.isEmpty()) {
            return;
        }

        List<WriteRequest> writeRequests = new ArrayList<>(keys.size());
        for (Map<String, AttributeValue> key : keys) {
            writeRequests.add(WriteRequest.builder()
                    .deleteRequest(DeleteRequest.builder().key(key).build())
                    .build());
        }
        batchWrite(writeRequests);
    }

    private List<Map<String, AttributeValue>> scanAllKeys() {
        List<Map<String, AttributeValue>> keys = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;
        try {
            do {
                var scanBuilder = software.amazon.awssdk.services.dynamodb.model.ScanRequest.builder()
                        .tableName(tableName)
                        .projectionExpression("#k")
                        .expressionAttributeNames(Map.of("#k", keyAttribute));
                if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                    scanBuilder.exclusiveStartKey(lastEvaluatedKey);
                }
                var scanResponse = dynamoDbClient.scan(scanBuilder.build());
                for (Map<String, AttributeValue> item : scanResponse.items()) {
                    keys.add(Map.of(keyAttribute, item.get(keyAttribute)));
                }
                lastEvaluatedKey = scanResponse.lastEvaluatedKey();
            } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
        } catch (ResourceNotFoundException e) {
            return Collections.emptyList();
        }
        return keys;
    }

    private boolean tableExists() {
        try {
            dynamoDbClient.describeTable(
                    DescribeTableRequest.builder().tableName(tableName).build());
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private void createTable(int dimension) {
        List<SearchSchemaElement> searchSchema = new ArrayList<>();
        for (String attribute : inlineFilterAttributes) {
            searchSchema.add(SearchSchemaElement.builder()
                    .attributeName(attribute)
                    .searchSchemaElementType(SearchSchemaElementType.INLINE_FILTER)
                    .build());
        }

        VectorIndex.Builder vectorIndexBuilder = VectorIndex.builder()
                .indexName(indexName)
                .vectorAttribute(VectorAttributeDefinition.builder()
                        .attributeName(vectorAttribute)
                        .build())
                .dimensions((long) dimension)
                .distanceFunction(distanceFunction)
                .projection(
                        Projection.builder().projectionType(ProjectionType.ALL).build());
        if (!searchSchema.isEmpty()) {
            vectorIndexBuilder.searchSchema(searchSchema);
        }

        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(keyAttribute)
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .keySchema(KeySchemaElement.builder()
                        .attributeName(keyAttribute)
                        .keyType(KeyType.HASH)
                        .build())
                .vectorIndexes(vectorIndexBuilder.build())
                .build();

        dynamoDbClient.createTable(request);
        waitForTableActive();
    }

    private void waitForTableActive() {
        long startTime = System.nanoTime();
        long timeoutNanos = TimeUnit.SECONDS.toNanos(SECONDS_TO_WAIT_FOR_TABLE);

        while (System.nanoTime() - startTime < timeoutNanos) {
            try {
                DescribeTableResponse response = dynamoDbClient.describeTable(
                        DescribeTableRequest.builder().tableName(tableName).build());
                if (response.table().tableStatus() == TableStatus.ACTIVE) {
                    return;
                }
            } catch (ResourceNotFoundException e) {
                // table not visible yet
            }
            sleep(1000);
        }
        throw new IllegalStateException("DynamoDB table " + tableName + " and its vector index were not ACTIVE within "
                + SECONDS_TO_WAIT_FOR_TABLE + " seconds");
    }

    private Map<String, AttributeValue> buildItem(String id, Embedding embedding, TextSegment textSegment) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(keyAttribute, AttributeValue.fromS(id));
        item.put(vectorAttribute, AttributeValue.fromL(toAttributeValues(embedding.vector())));

        if (textSegment != null) {
            item.put(textMetadataKey, AttributeValue.fromS(textSegment.text()));
            if (textSegment.metadata() != null) {
                textSegment.metadata().toMap().forEach((key, value) -> {
                    if (value == null) {
                        return;
                    }
                    if (key.equals(keyAttribute) || key.equals(vectorAttribute) || key.equals(textMetadataKey)) {
                        throw new IllegalArgumentException("Metadata key '" + key
                                + "' collides with a reserved DynamoDB attribute (id / vector / text). "
                                + "Rename the metadata key, or configure a different keyAttribute, "
                                + "vectorAttribute or textMetadataKey on the store.");
                    }
                    item.put(key, DynamoDbAttributeCodec.encode(value));
                });
            }
        }

        return item;
    }

    private TextSegment extractTextSegment(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty() || !item.containsKey(textMetadataKey)) {
            return null;
        }

        String text = item.get(textMetadataKey).s();
        if (text == null) {
            return null;
        }

        Map<String, Object> extractedMetadata = new HashMap<>();
        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            String key = entry.getKey();
            if (key.equals(keyAttribute) || key.equals(vectorAttribute) || key.equals(textMetadataKey)) {
                continue;
            }
            Object value = DynamoDbAttributeCodec.decode(entry.getValue());
            if (value != null) {
                extractedMetadata.put(key, value);
            }
        }

        return TextSegment.from(text, extractedMetadata.isEmpty() ? new Metadata() : new Metadata(extractedMetadata));
    }

    // Manual loop avoids boxing overhead from streams
    private List<AttributeValue> toAttributeValues(float[] array) {
        List<AttributeValue> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(AttributeValue.fromN(Float.toString(f)));
        }
        return list;
    }

    private double distanceToScore(Double score) {
        if (score == null) {
            return 0.0;
        }
        switch (distanceFunction) {
            case COSINE:
                // DynamoDB cosine score ranges from 0 (identical) to 2 (opposite): score = 1 - cosineSimilarity.
                return RelevanceScore.fromCosineSimilarity(1.0 - score);
            case DOT_PRODUCT:
                // Higher is better; map into (0, 1] with a monotonic squashing function.
                return 1.0 / (1.0 + Math.exp(-score));
            case EUCLIDEAN:
            default:
                // Lower distance is better.
                return 1.0 / (1.0 + score);
        }
    }

    /**
     * Closes the underlying {@link DynamoDbClient}, but only if this store created it. A client
     * supplied via {@link Builder#dynamoDbClient(DynamoDbClient)} is owned by the caller and left open.
     */
    @Override
    public void close() {
        if (ownsClient && dynamoDbClient != null) {
            dynamoDbClient.close();
        }
    }
}
