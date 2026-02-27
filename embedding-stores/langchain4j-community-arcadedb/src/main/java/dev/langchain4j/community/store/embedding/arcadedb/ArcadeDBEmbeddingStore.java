package dev.langchain4j.community.store.embedding.arcadedb;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.Identifiable;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.index.vector.HnswVectorIndex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import com.arcadedb.schema.VertexType;
import com.arcadedb.utility.Pair;
import com.github.jelmerk.knn.DistanceFunctions;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.PROPERTY_EMBEDDING;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.PROPERTY_ID;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.PROPERTY_DELETED;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.PROPERTY_TEXT;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.embeddingToFloatArray;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.extractMetadata;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.toEmbeddingMatch;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

/**
 * ArcadeDB implementation of {@link EmbeddingStore} using embedded database with native HNSW vector indexing.
 *
 * <p>Unlike most LangChain4j embedding stores that connect to external servers, ArcadeDB runs
 * <strong>embedded in the same JVM</strong> — zero network overhead, zero serialization cost,
 * with persistence, HNSW vector indexing, and graph capabilities.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.builder()
 *     .databasePath("/tmp/mydb")
 *     .dimension(384)
 *     .build();
 * }</pre>
 */
public class ArcadeDBEmbeddingStore implements EmbeddingStore<TextSegment>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ArcadeDBEmbeddingStore.class);
    private static final String DEFAULT_EDGE_TYPE = "HnswEdge";

    private final Database database;
    private final String typeName;
    private final String quotedTypeName;
    private final String edgeType;
    private final String metadataPrefix;
    private final int dimension;
    private HnswVectorIndex<Object, float[], Float> vectorIndex;

    private ArcadeDBEmbeddingStore(Builder builder) {
        this.typeName = builder.typeName;
        this.quotedTypeName = "`" + builder.typeName + "`";
        this.edgeType = builder.edgeType;
        this.metadataPrefix = builder.metadataPrefix;
        this.dimension = builder.dimension;
        if (builder.database != null) {
            this.database = builder.database;
        } else {
            ensureNotBlank(builder.databasePath, "databasePath");
            DatabaseFactory factory = new DatabaseFactory(builder.databasePath);
            this.database = factory.exists() ? factory.open() : factory.create();
        }

        initSchema(builder);
    }

    @SuppressWarnings("unchecked")
    private void initSchema(Builder builder) {
        database.transaction(() -> {
            Schema schema = database.getSchema();

            // Create or get the vertex type
            VertexType vertexType = schema.existsType(typeName)
                    ? (VertexType) schema.getType(typeName)
                    : schema.createVertexType(typeName, 1);

            if (!vertexType.existsProperty(PROPERTY_ID)) {
                vertexType.createProperty(PROPERTY_ID, Type.STRING);
            }
            if (!vertexType.existsProperty(PROPERTY_EMBEDDING)) {
                vertexType.createProperty(PROPERTY_EMBEDDING, Type.ARRAY_OF_FLOATS);
            }
            if (!vertexType.existsProperty(PROPERTY_TEXT)) {
                vertexType.createProperty(PROPERTY_TEXT, Type.STRING);
            }
            if (!vertexType.existsProperty(PROPERTY_DELETED)) {
                vertexType.createProperty(PROPERTY_DELETED, Type.BOOLEAN);
            }

            // Create unique index on id
            if (vertexType.getPolymorphicIndexByProperties(PROPERTY_ID) == null) {
                schema.createTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true, typeName, PROPERTY_ID);
            }
        });

        // Check if vector index already exists
        try {
            String indexName = typeName + "[" + PROPERTY_ID + "," + PROPERTY_EMBEDDING + "]";
            var existingIndex = database.getSchema().getIndexByName(indexName);
            if (existingIndex instanceof HnswVectorIndex) {
                vectorIndex = (HnswVectorIndex<Object, float[], Float>) existingIndex;
                return;
            }
        } catch (Exception e) {
            // Index doesn't exist yet, create it
        }

        // Create HNSW vector index
        database.transaction(() -> {
            var indexBuilder = database.getSchema().buildVectorIndex()
                    .withVertexType(typeName)
                    .withEdgeType(edgeType)
                    .withVectorProperty(PROPERTY_EMBEDDING, Type.ARRAY_OF_FLOATS)
                    .withIdProperty(PROPERTY_ID)
                    .withDeletedProperty(PROPERTY_DELETED)
                    .withDimensions(dimension)
                    .withDistanceFunction(DistanceFunctions.FLOAT_COSINE_DISTANCE)
                    .withDistanceComparator((Comparator<Float>) Float::compareTo)
                    .withM(builder.m)
                    .withEf(builder.ef)
                    .withEfConstruction(builder.efConstruction);
            // Set the index type required by the IndexFactory
            indexBuilder.withType(Schema.INDEX_TYPE.HNSW);
            vectorIndex = (HnswVectorIndex<Object, float[], Float>) indexBuilder.create();
        });
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addAll(List.of(id), List.of(embedding), null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addAll(List.of(id), List.of(embedding), List.of(textSegment));
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>(embeddings.size());
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(randomUUID());
        }
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids and embeddings must have the same size");
        if (segments != null) {
            ensureTrue(ids.size() == segments.size(), "ids, embeddings, and segments must have the same size");
        }

        // Save vertices first, then add to HNSW index in separate steps
        // HNSW needs committed vertices to build graph connections
        List<Vertex> savedVertices = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            final String id = ids.get(i);

            database.transaction(() -> {
                // Soft-delete existing vertex with same id (upsert)
                softDeleteById(id);

                MutableVertex vertex = database.newVertex(typeName);
                vertex.set(PROPERTY_ID, id);
                vertex.set(PROPERTY_EMBEDDING, embeddingToFloatArray(embeddings.get(idx)));

                if (segments != null && segments.get(idx) != null) {
                    TextSegment segment = segments.get(idx);
                    vertex.set(PROPERTY_TEXT, segment.text());

                    Metadata metadata = segment.metadata();
                    if (metadata != null) {
                        for (Map.Entry<String, Object> entry : metadata.toMap().entrySet()) {
                            String propName = metadataPrefix + entry.getKey();
                            Object value = entry.getValue();
                            // Convert UUID to String to avoid type mismatch in comparisons
                            if (value instanceof UUID) {
                                value = value.toString();
                            }
                            vertex.set(propName, value);
                        }
                    }
                }

                vertex.save();
                savedVertices.add(vertex.asVertex());
            });
        }

        // Add to HNSW index after vertices are committed
        for (Vertex savedVertex : savedVertices) {
            database.transaction(() -> {
                vectorIndex.add(savedVertex);
            });
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        ensureNotNull(request, "request");
        ensureNotNull(request.queryEmbedding(), "queryEmbedding");

        float[] queryVector = embeddingToFloatArray(request.queryEmbedding());
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        List<EmbeddingMatch<TextSegment>> matches;

        if (filter != null) {
            matches = searchWithFilter(queryVector, maxResults, minScore, filter);
        } else {
            matches = searchWithoutFilter(queryVector, maxResults, minScore);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private boolean isDeleted(Vertex vertex) {
        Object deleted = vertex.get(PROPERTY_DELETED);
        return Boolean.TRUE.equals(deleted);
    }

    private List<EmbeddingMatch<TextSegment>> searchWithoutFilter(float[] queryVector, int maxResults, double minScore) {
        // Overfetch from HNSW and post-filter deleted items, because the HNSW callback
        // receives cached vertex objects that don't reflect recent soft-delete updates.
        int fetchSize = Math.max(maxResults * 4, maxResults + 100);
        List<Pair<Identifiable, ? extends Number>> neighbors = vectorIndex.findNeighborsFromVector(
                queryVector, fetchSize);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (Pair<Identifiable, ? extends Number> neighbor : neighbors) {
            if (matches.size() >= maxResults) break;
            double distance = neighbor.getSecond().doubleValue();
            double cosineSimilarity = 1.0 - Math.max(0.0, distance);
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);
            if (score >= minScore) {
                try {
                    Vertex vertex = neighbor.getFirst().getRecord().asVertex();
                    if (isDeleted(vertex)) continue;
                    matches.add(toEmbeddingMatch(vertex, score, metadataPrefix));
                } catch (Exception e) {
                    log.warn("Failed to load vertex {}: {}", neighbor.getFirst(), e.getMessage());
                }
            }
        }
        return matches;
    }

    private List<EmbeddingMatch<TextSegment>> searchWithFilter(float[] queryVector, int maxResults, double minScore, Filter filter) {
        // Java-based filter evaluation avoids SQL type conversion issues
        // (UUID, Double, Float type mismatches in ArcadeDB SQL).
        int fetchSize = Math.max(maxResults * 4, maxResults + 100);
        List<Pair<Identifiable, ? extends Number>> neighbors = vectorIndex.findNeighborsFromVector(
                queryVector, fetchSize);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (Pair<Identifiable, ? extends Number> neighbor : neighbors) {
            if (matches.size() >= maxResults) break;
            double distance = neighbor.getSecond().doubleValue();
            double cosineSimilarity = 1.0 - Math.max(0.0, distance);
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);
            if (score >= minScore) {
                try {
                    Vertex vertex = neighbor.getFirst().getRecord().asVertex();
                    if (isDeleted(vertex)) continue;
                    Map<String, Object> metadata = extractMetadata(vertex, metadataPrefix);
                    if (!matchesFilter(filter, metadata)) continue;
                    matches.add(toEmbeddingMatch(vertex, score, metadataPrefix));
                } catch (Exception e) {
                    log.warn("Failed to load vertex {}: {}", neighbor.getFirst(), e.getMessage());
                }
            }
        }
        return matches;
    }

    // === Java-based filter evaluation ===

    private static boolean matchesFilter(Filter filter, Map<String, Object> metadata) {
        if (filter instanceof IsEqualTo f) {
            Object actual = metadata.get(f.key());
            return actual != null && valueEquals(actual, f.comparisonValue());
        } else if (filter instanceof IsNotEqualTo f) {
            Object actual = metadata.get(f.key());
            return actual == null || !valueEquals(actual, f.comparisonValue());
        } else if (filter instanceof IsGreaterThan f) {
            Object actual = metadata.get(f.key());
            return actual != null && compareValues(actual, f.comparisonValue()) > 0;
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            Object actual = metadata.get(f.key());
            return actual != null && compareValues(actual, f.comparisonValue()) >= 0;
        } else if (filter instanceof IsLessThan f) {
            Object actual = metadata.get(f.key());
            return actual != null && compareValues(actual, f.comparisonValue()) < 0;
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            Object actual = metadata.get(f.key());
            return actual != null && compareValues(actual, f.comparisonValue()) <= 0;
        } else if (filter instanceof IsIn f) {
            Object actual = metadata.get(f.key());
            return actual != null && f.comparisonValues().stream().anyMatch(v -> valueEquals(actual, v));
        } else if (filter instanceof IsNotIn f) {
            Object actual = metadata.get(f.key());
            return actual == null || f.comparisonValues().stream().noneMatch(v -> valueEquals(actual, v));
        } else if (filter instanceof ContainsString f) {
            Object actual = metadata.get(f.key());
            return actual instanceof String s && s.contains(f.comparisonValue());
        } else if (filter instanceof And f) {
            return matchesFilter(f.left(), metadata) && matchesFilter(f.right(), metadata);
        } else if (filter instanceof Or f) {
            return matchesFilter(f.left(), metadata) || matchesFilter(f.right(), metadata);
        } else if (filter instanceof Not f) {
            return !matchesFilter(f.expression(), metadata);
        }
        throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return Objects.equals(objectToString(a), objectToString(b));
    }

    private static int compareValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        return objectToString(a).compareTo(objectToString(b));
    }

    private static String objectToString(Object o) {
        return o == null ? "" : o.toString();
    }

    @Override
    public void remove(String id) {
        ensureNotBlank(id, "id");
        database.transaction(() -> softDeleteById(id));
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }
        database.transaction(() -> {
            for (String id : ids) {
                softDeleteById(id);
            }
        });
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        database.transaction(() -> {
            try (ResultSet rs = database.query("sql", "SELECT FROM " + quotedTypeName
                    + " WHERE (" + PROPERTY_DELETED + " IS NULL OR " + PROPERTY_DELETED + " != true)")) {
                while (rs.hasNext()) {
                    Result result = rs.next();
                    result.getVertex().ifPresent(v -> {
                        Map<String, Object> metadata = extractMetadata(v, metadataPrefix);
                        if (matchesFilter(filter, metadata)) {
                            v.modify().set(PROPERTY_DELETED, true).save();
                        }
                    });
                }
            }
        });
    }

    @Override
    public void removeAll() {
        database.transaction(() -> {
            database.command("sql", "UPDATE " + quotedTypeName + " SET " + PROPERTY_DELETED + " = true");
        });
    }

    private void softDeleteById(String id) {
        try (ResultSet rs = database.query("sql",
                "SELECT FROM " + quotedTypeName + " WHERE " + PROPERTY_ID + " = ?", id)) {
            while (rs.hasNext()) {
                Result result = rs.next();
                result.getVertex().ifPresent(v -> {
                    v.modify().set(PROPERTY_DELETED, true).save();
                    vectorIndex.remove(new Object[]{id});
                });
            }
        }
    }

    @Override
    public void close() {
        if (database != null && database.isOpen()) {
            database.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String databasePath;
        private Database database;
        private String typeName = "Document";
        private String edgeType = DEFAULT_EDGE_TYPE;
        private String metadataPrefix = "";
        private int dimension = 384;
        private int m = 16;
        private int ef = 10;
        private int efConstruction = 200;

        /**
         * Path for the embedded ArcadeDB database directory.
         */
        public Builder databasePath(String databasePath) {
            this.databasePath = databasePath;
            return this;
        }

        /**
         * Use an existing ArcadeDB {@link Database} instance instead of creating one.
         */
        public Builder database(Database database) {
            this.database = database;
            return this;
        }

        /**
         * The vertex type name to store embeddings. Default: "Document".
         */
        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        /**
         * The edge type name for HNSW graph connections. Default: "HnswEdge".
         */
        public Builder edgeType(String edgeType) {
            this.edgeType = edgeType;
            return this;
        }

        /**
         * Prefix for metadata properties on the vertex. Default: "" (no prefix).
         */
        public Builder metadataPrefix(String metadataPrefix) {
            this.metadataPrefix = metadataPrefix;
            return this;
        }

        /**
         * The dimension of embedding vectors. Default: 384.
         */
        public Builder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * HNSW M parameter (max connections per node). Default: 16.
         */
        public Builder m(int m) {
            this.m = m;
            return this;
        }

        /**
         * HNSW ef parameter (search beam width). Default: 10.
         */
        public Builder ef(int ef) {
            this.ef = ef;
            return this;
        }

        /**
         * HNSW efConstruction parameter (construction beam width). Default: 200.
         */
        public Builder efConstruction(int efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        public ArcadeDBEmbeddingStore build() {
            return new ArcadeDBEmbeddingStore(this);
        }
    }
}
