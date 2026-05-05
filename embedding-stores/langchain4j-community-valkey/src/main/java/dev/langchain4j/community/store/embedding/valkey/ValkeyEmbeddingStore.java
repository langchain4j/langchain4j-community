package dev.langchain4j.community.store.embedding.valkey;

import static dev.langchain4j.community.store.embedding.valkey.ValkeyJsonUtils.toJson;
import static dev.langchain4j.community.store.embedding.valkey.ValkeyJsonUtils.toProperties;
import static dev.langchain4j.community.store.embedding.valkey.ValkeySchema.JSON_KEY;
import static dev.langchain4j.community.store.embedding.valkey.ValkeySchema.JSON_PATH_PREFIX;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static glide.api.models.GlideString.gs;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import glide.api.GlideClient;
import glide.api.commands.servermodules.FT;
import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.TagField;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.commands.scan.ScanOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a <a href="https://valkey.io/">Valkey</a> index as an embedding store.
 *
 * <p>Supports COSINE, IP (inner product), and L2 (Euclidean) distance metrics.
 * The score calculation is automatically adjusted based on the configured metric type.</p>
 *
 * <p>Uses the official <a href="https://github.com/valkey-io/valkey-glide">valkey-glide</a> client
 * and Valkey's built-in vector search capabilities (Valkey 8+).</p>
 *
 * <p><b>NOTE: </b> For filter, Valkey only supports below filter types:</p>
 *
 * <ul>
 *     <li>NumericType: eq/neq/gt/gte/lt/lte</li>
 *     <li>TagType: eq/neq/in/notIn</li>
 *     <li>TextType: eq/neq/in/notIn</li>
 * </ul>
 */
public class ValkeyEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ValkeyEmbeddingStore.class);

    private static final String QUERY_TEMPLATE = "%s=>[KNN %d @%s $BLOB]";
    private static final long DEFAULT_OPERATION_TIMEOUT_SECONDS = 60;
    private static final int MAX_REMOVE_ITERATIONS = 1000;

    private final GlideClient client;
    private final ValkeySchema schema;
    private final ValkeyMetadataFilterMapper filterMapper;
    private final long operationTimeoutSeconds;

    /**
     * Creates an instance of ValkeyEmbeddingStore.
     *
     * @param client                   Instance of a GlideClient
     * @param indexName                The name of the index (optional). Default value: "embedding-index".
     * @param prefix                   The prefix of the key which should end with a colon (e.g., "embedding:") (optional). Default value: "embedding:".
     * @param dimension                Embedding vector dimension
     * @param metadataConfig           Metadata config to map metadata key to field info. (optional)
     * @param operationTimeoutSeconds  Timeout in seconds for each Valkey operation (optional). Default value: 60.
     */
    public ValkeyEmbeddingStore(
            GlideClient client,
            String indexName,
            String prefix,
            Integer dimension,
            Map<String, FieldInfo> metadataConfig,
            Long operationTimeoutSeconds) {
        ensureNotNull(client, "client");

        this.client = client;
        this.operationTimeoutSeconds = getOrDefault(operationTimeoutSeconds, DEFAULT_OPERATION_TIMEOUT_SECONDS);
        this.schema = ValkeySchema.builder()
                .indexName(getOrDefault(indexName, "embedding-index"))
                .prefix(getOrDefault(prefix, "embedding:"))
                .dimension(dimension)
                .metadataConfig(metadataConfig != null ? new HashMap<>(metadataConfig) : new HashMap<>())
                .build();
        this.filterMapper = new ValkeyMetadataFilterMapper(deriveFieldTypeMap(metadataConfig));

        if (!isIndexExist(schema.getIndexName())) {
            ensureNotNull(dimension, "dimension");
            createIndex(schema.getIndexName());
        }
    }

    public static Builder builder() {
        return new Builder();
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
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        String filterExpression = filterMapper.mapToFilter(request.filter());
        validateFilterExpression(filterExpression);
        String query = format(QUERY_TEMPLATE, filterExpression, request.maxResults(), schema.getVectorFieldName());

        byte[] vectorBytes = toByteArray(request.queryEmbedding().vector());

        FTSearchOptions searchOptions = FTSearchOptions.builder()
                .params(Map.of(gs("BLOB"), gs(vectorBytes)))
                .limit(0, request.maxResults())
                .build();

        Object[] result = awaitResult(FT.search(client, schema.getIndexName(), query, searchOptions));

        List<EmbeddingMatch<TextSegment>> matches = parseSearchResults(result, request.minScore());

        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        String[] valkeyKeys = ids.stream().map(id -> schema.getPrefix() + id).toArray(String[]::new);
        awaitResult(client.del(valkeyKeys));
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        String filterExpression = filterMapper.mapToFilter(filter);
        validateFilterExpression(filterExpression);

        // Valkey Search requires a vector query component (=>) for all FT.SEARCH queries.
        // Use a zero vector with a large K to find matching documents in batches.
        int batchSize = 10000;
        byte[] zeroVector = new byte[schema.getDimension() * Float.BYTES];
        String query = format(QUERY_TEMPLATE, filterExpression, batchSize, schema.getVectorFieldName());

        FTSearchOptions searchOptions = FTSearchOptions.builder()
                .params(Map.of(gs("BLOB"), gs(zeroVector)))
                .limit(0, batchSize)
                .build();

        // Loop until all matching documents are removed, with a safety guard against infinite loops
        String[] keys;
        int iteration = 0;
        do {
            if (++iteration > MAX_REMOVE_ITERATIONS) {
                log.warn("removeAll(Filter) exceeded {} iterations, aborting", MAX_REMOVE_ITERATIONS);
                break;
            }
            Object[] result = awaitResult(FT.search(client, schema.getIndexName(), query, searchOptions));
            keys = extractKeysFromSearchResult(result);
            if (keys.length > 0) {
                awaitResult(client.del(keys));
            }
        } while (keys.length >= batchSize);
    }

    @Override
    public void removeAll() {
        Set<String> matchingKeys = new HashSet<>();
        String nextCursor = "0";

        ScanOptions scanOptions = ScanOptions.builder()
                .matchPattern(schema.getPrefix() + "*")
                .count(1000L)
                .build();

        do {
            Object[] scanResult = awaitResult(client.scan(nextCursor, scanOptions));
            nextCursor = scanResult[0].toString();
            Object[] keys = (Object[]) scanResult[1];
            for (Object key : keys) {
                matchingKeys.add(key.toString());
            }
        } while (!"0".equals(nextCursor));

        if (matchingKeys.isEmpty()) {
            return;
        }

        awaitResult(client.del(matchingKeys.toArray(new String[0])));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to valkey");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        int size = ids.size();
        List<CompletableFuture<String>> futures = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);

            Map<String, Object> fields = new HashMap<>();
            fields.put(schema.getVectorFieldName(), embedding.vector());
            if (textSegment != null) {
                fields.put(schema.getScalarFieldName(), textSegment.text());
                fields.putAll(textSegment.metadata().toMap());
            }

            String key = schema.getPrefix() + id;
            String json = toJson(fields);
            futures.add(Json.set(client, key, "$", json));
        }

        // Await all writes concurrently for better throughput
        List<String> responses = new ArrayList<>(size);
        for (CompletableFuture<String> future : futures) {
            responses.add(awaitResult(future));
        }

        Optional<String> errResponse =
                responses.stream().filter(response -> !"OK".equals(response)).findAny();
        if (errResponse.isPresent()) {
            if (log.isErrorEnabled()) {
                log.error("add embedding failed, msg={}", errResponse.get());
            }
            throw new ValkeyRequestFailedException("add embedding failed, msg=" + errResponse.get());
        }
    }

    public ValkeySchema getSchema() {
        return schema;
    }

    /**
     * Closes the underlying GlideClient connection.
     *
     * <p>Note: If the client was provided externally via the builder, the caller is responsible
     * for managing its lifecycle. This method is provided for convenience when the store owns
     * the client exclusively.</p>
     */
    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing Valkey client", e);
            }
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void createIndex(String indexName) {
        FTCreateOptions createOptions = FTCreateOptions.builder()
                .dataType(FTCreateOptions.DataType.JSON)
                .prefixes(new String[] {schema.getPrefix()})
                .build();

        String res = awaitResult(FT.create(client, indexName, schema.toFieldInfoArray(), createOptions));
        if (!"OK".equals(res)) {
            if (log.isErrorEnabled()) {
                log.error("create index error, msg={}", res);
            }
            throw new ValkeyRequestFailedException("create index error, msg=" + res);
        }
    }

    private boolean isIndexExist(String indexName) {
        GlideString[] indexes = awaitResult(FT.list(client));
        if (indexes == null) {
            return false;
        }
        for (GlideString index : indexes) {
            if (indexName.equals(index.getString())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingMatch<TextSegment>> parseSearchResults(Object[] result, double minScore) {
        if (result == null || result.length < 2) {
            return new ArrayList<>();
        }

        Long totalCount = (Long) result[0];
        if (totalCount == 0) {
            return new ArrayList<>();
        }

        Map<GlideString, Map<GlideString, GlideString>> documents =
                (Map<GlideString, Map<GlideString, GlideString>>) result[1];

        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        String scoreFieldName = schema.getScoreFieldName();
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        for (Map.Entry<GlideString, Map<GlideString, GlideString>> entry : documents.entrySet()) {
            String documentKey = entry.getKey().getString();
            Map<GlideString, GlideString> attrs = entry.getValue();

            // Extract score
            GlideString scoreValue = attrs.get(gs(scoreFieldName));
            if (scoreValue == null) {
                continue;
            }
            double distance = Double.parseDouble(scoreValue.getString());
            double score = distanceToScore(distance, schema.getMetricType());

            if (score < minScore) {
                continue;
            }

            // Extract ID by stripping prefix
            String id = documentKey.substring(schema.getPrefix().length());

            // Extract JSON document
            GlideString jsonValue = attrs.get(gs(JSON_KEY));
            if (jsonValue == null) {
                continue;
            }

            Map<String, Object> properties = toProperties(jsonValue.getString());

            // Reconstruct embedding
            List<Double> vectors = (List<Double>) properties.get(schema.getVectorFieldName());
            Embedding embedding =
                    Embedding.from(vectors.stream().map(Double::floatValue).collect(toList()));

            // Reconstruct text segment
            String text = properties.containsKey(schema.getScalarFieldName())
                    ? (String) properties.get(schema.getScalarFieldName())
                    : null;
            TextSegment textSegment = null;
            if (text != null) {
                Map<String, Object> metadata = schema.getMetadataConfig().keySet().stream()
                        .filter(properties::containsKey)
                        .collect(toMap(metadataKey -> metadataKey, properties::get));
                textSegment = TextSegment.from(text, Metadata.from(metadata));
            }

            matches.add(new EmbeddingMatch<>(score, id, embedding, textSegment));
        }

        // Sort by score descending (highest relevance first)
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));

        return matches;
    }

    @SuppressWarnings("unchecked")
    private String[] extractKeysFromSearchResult(Object[] result) {
        if (result == null || result.length < 2) {
            return new String[0];
        }

        Long totalCount = (Long) result[0];
        if (totalCount == 0) {
            return new String[0];
        }

        Map<GlideString, Map<GlideString, GlideString>> documents =
                (Map<GlideString, Map<GlideString, GlideString>>) result[1];

        if (documents == null || documents.isEmpty()) {
            return new String[0];
        }

        return documents.keySet().stream().map(GlideString::getString).toArray(String[]::new);
    }

    private <T> T awaitResult(CompletableFuture<T> future) {
        try {
            return future.get(operationTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new ValkeyRequestFailedException(
                    "Valkey operation timed out after " + operationTimeoutSeconds + " seconds", e);
        } catch (ExecutionException e) {
            throw new ValkeyRequestFailedException(
                    "Valkey operation failed: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValkeyRequestFailedException("Valkey operation interrupted", e);
        }
    }

    private static byte[] toByteArray(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /**
     * Validates that a filter expression does not contain the {@code =>} token, which separates
     * a filter from a KNN clause in FT.SEARCH. If a metadata value contains {@code =>}, it could
     * inject a KNN query that bypasses filters.
     */
    private static void validateFilterExpression(String filterExpression) {
        if (filterExpression != null && filterExpression.contains("=>")) {
            throw new IllegalArgumentException("Filter expression must not contain '=>'");
        }
    }

    /**
     * Converts a Valkey distance value to a relevance score based on the configured metric type.
     *
     * <ul>
     *     <li>COSINE: score = (2 - distance) / 2 (distance range [0, 2] maps to score [1, 0])</li>
     *     <li>IP: score = 1 - distance (inner product distance is 1 - similarity)</li>
     *     <li>L2: score = 1 / (1 + distance) (Euclidean distance [0, ∞) maps to score (0, 1])</li>
     * </ul>
     */
    private static double distanceToScore(double distance, MetricType metricType) {
        return switch (metricType) {
            case COSINE -> (2 - distance) / 2;
            case IP -> 1 - distance;
            case L2 -> 1.0 / (1.0 + distance);
        };
    }

    private static Map<String, ValkeyMetadataFilterMapper.FieldType> deriveFieldTypeMap(
            Map<String, FieldInfo> metadataConfig) {
        if (metadataConfig == null || metadataConfig.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, ValkeyMetadataFilterMapper.FieldType> fieldTypeMap = new HashMap<>();
        for (Map.Entry<String, FieldInfo> entry : metadataConfig.entrySet()) {
            String key = entry.getKey();
            FieldInfo fieldInfo = entry.getValue();
            ValkeyMetadataFilterMapper.FieldType fieldType = inferFieldType(fieldInfo);
            fieldTypeMap.put(key, fieldType);
        }
        return fieldTypeMap;
    }

    /**
     * Infers the field type from a {@link FieldInfo} by inspecting its internal field object type.
     * Falls back to parsing {@code toArgs()} output if the field type cannot be determined from the object type.
     */
    private static ValkeyMetadataFilterMapper.FieldType inferFieldType(FieldInfo fieldInfo) {
        if (fieldInfo == null) {
            return ValkeyMetadataFilterMapper.FieldType.TAG;
        }

        // First, try to determine field type from the FieldInfo's toArgs() output.
        // This inspects the serialized arguments for known type keywords.
        GlideString[] args = fieldInfo.toArgs();
        for (GlideString arg : args) {
            String argStr = arg.getString();
            if ("NUMERIC".equals(argStr)) {
                return ValkeyMetadataFilterMapper.FieldType.NUMERIC;
            } else if ("TEXT".equals(argStr)) {
                return ValkeyMetadataFilterMapper.FieldType.TEXT;
            } else if ("TAG".equals(argStr)) {
                return ValkeyMetadataFilterMapper.FieldType.TAG;
            }
        }
        return ValkeyMetadataFilterMapper.FieldType.TAG;
    }

    public static class Builder {

        private GlideClient client;
        private String indexName;
        private String prefix;
        private Integer dimension;
        private Map<String, FieldInfo> metadataConfig = new HashMap<>();
        private Long operationTimeoutSeconds;

        /**
         * @param client GlideClient instance
         * @return builder
         */
        public Builder client(GlideClient client) {
            this.client = client;
            return this;
        }

        /**
         * @param indexName The name of the index (optional). Default value: "embedding-index".
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * @param prefix The prefix of the key, should end with a colon (e.g., "embedding:") (optional). Default value: "embedding:".
         * @return builder
         */
        public Builder prefix(String prefix) {
            if (prefix != null && !prefix.endsWith(":")) {
                prefix = prefix + ":";
            }
            this.prefix = prefix;
            return this;
        }

        /**
         * @param dimension embedding vector dimension (optional)
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param metadataKeys Metadata keys that should be persisted (optional). All metadata will be stored as
         *                     <a href="https://valkey.io/docs/topics/query/">Tag Fields</a>.
         *                     See {@link #metadataConfig(Map)} if you want to customize your metadata field types.
         * @return builder
         * @see #metadataConfig(Map)
         */
        public Builder metadataKeys(Collection<String> metadataKeys) {
            if (!isNullOrEmpty(metadataKeys)) {
                metadataKeys.forEach(metadataKey -> metadataConfig.put(
                        metadataKey,
                        new FieldInfo(JSON_PATH_PREFIX + metadataKey, metadataKey, new TagField(',', true))));
            }
            return this;
        }

        /**
         * @param metadataConfig Metadata config to map metadata key to field info. (optional)
         * @return builder
         */
        public Builder metadataConfig(Map<String, FieldInfo> metadataConfig) {
            this.metadataConfig = metadataConfig;
            return this;
        }

        /**
         * @param operationTimeoutSeconds Timeout in seconds for each Valkey operation (optional). Default value: 60.
         * @return builder
         */
        public Builder operationTimeoutSeconds(Long operationTimeoutSeconds) {
            this.operationTimeoutSeconds = operationTimeoutSeconds;
            return this;
        }

        public ValkeyEmbeddingStore build() {
            ensureNotNull(client, "client");
            return new ValkeyEmbeddingStore(client, indexName, prefix, dimension, metadataConfig, operationTimeoutSeconds);
        }
    }
}
