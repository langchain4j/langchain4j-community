package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.PROPERTY_DELETED;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.embeddingToFloatArray;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.extractMetadata;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.toEmbeddingMatch;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.RID;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.index.IndexInternal;
import com.arcadedb.index.TypeIndex;
import com.arcadedb.index.vector.LSMVectorIndex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import com.arcadedb.remote.RemoteServer;
import com.arcadedb.schema.Schema;
import com.arcadedb.schema.Type;
import com.arcadedb.schema.VertexType;
import com.arcadedb.utility.Pair;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link EmbeddingStore} using <a href="https://arcadedb.com/">ArcadeDB</a>
 * with LSM_VECTOR (JVector-based HNSW) vector indexing.
 *
 * <p>Two modes of operation are supported:
 * <ul>
 *   <li><b>Remote mode</b>: Connects to a remote ArcadeDB server via HTTP.
 *       Use {@link #builder()} to configure. Supports all filter types except
 *       {@code ContainsString}.</li>
 *   <li><b>Embedded mode</b>: ArcadeDB runs embedded within the same JVM, offering zero network
 *       overhead and zero serialization cost, with persistence and native HNSW indexing.
 *       Use {@link #embeddedBuilder()} to configure. Supports all filter types including
 *       {@code ContainsString}. Uses soft-delete to preserve HNSW graph connectivity.</li>
 * </ul>
 */
public class ArcadeDBEmbeddingStore implements EmbeddingStore<TextSegment>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ArcadeDBEmbeddingStore.class);

    // Remote mode property names
    static final String ID_PROPERTY = "doc_id";
    static final String EMBEDDING_PROPERTY = "embedding";
    static final String TEXT_PROPERTY = "text";

    // Embedded mode property names (from ArcadeDBEmbeddingUtils)
    private static final String EMBEDDED_ID = ArcadeDBEmbeddingUtils.PROPERTY_ID;
    private static final String EMBEDDED_EMBEDDING = ArcadeDBEmbeddingUtils.PROPERTY_EMBEDDING;
    private static final String EMBEDDED_TEXT = ArcadeDBEmbeddingUtils.PROPERTY_TEXT;

    // Shared defaults
    private static final String DEFAULT_TYPE_NAME = "EmbeddingDocument";
    private static final String DEFAULT_SIMILARITY = "COSINE";
    private static final int DEFAULT_MAX_CONNECTIONS = 16;
    private static final int DEFAULT_BEAM_WIDTH = 100;

    // Remote-specific defaults
    private static final int DEFAULT_PORT = 2480;
    private static final String REMOTE_DEFAULT_METADATA_PREFIX = "meta_";

    // Embedded-specific defaults
    private static final String EMBEDDED_DEFAULT_METADATA_PREFIX = "";

    // Mode flag
    private final boolean embeddedMode;

    // Shared fields
    private final String typeName;
    private final String metadataPrefix;

    // Remote mode fields (null in embedded mode)
    private final RemoteDatabase remoteDatabase;
    private final ArcadeDBMetadataFilterMapper filterMapper;

    // Embedded mode fields (null in remote mode)
    private final Database embeddedDatabase;
    private LSMVectorIndex vectorIndex;

    // ===== Remote mode constructor =====

    private ArcadeDBEmbeddingStore(
            RemoteDatabase remoteDatabase,
            String typeName,
            int dimension,
            String similarityFunction,
            int maxConnections,
            int beamWidth,
            String metadataPrefix) {
        this.embeddedMode = false;
        this.remoteDatabase = ensureNotNull(remoteDatabase, "database");
        this.embeddedDatabase = null;
        this.typeName = ensureNotBlank(typeName, "typeName");
        this.metadataPrefix = metadataPrefix;
        this.filterMapper = new ArcadeDBMetadataFilterMapper(metadataPrefix);
        initRemoteSchema(dimension, similarityFunction, maxConnections, beamWidth);
    }

    // ===== Embedded mode constructor =====

    private ArcadeDBEmbeddingStore(EmbeddedBuilder builder) {
        this.embeddedMode = true;
        this.remoteDatabase = null;
        this.filterMapper = null;
        this.typeName = builder.typeName;
        this.metadataPrefix = builder.metadataPrefix;
        if (builder.database != null) {
            this.embeddedDatabase = builder.database;
        } else {
            ensureNotBlank(builder.databasePath, "databasePath");
            DatabaseFactory factory = new DatabaseFactory(builder.databasePath);
            this.embeddedDatabase = factory.exists() ? factory.open() : factory.create();
        }
        initEmbeddedSchema(builder);
    }

    /**
     * Returns a builder for remote mode (connects to an ArcadeDB server via HTTP).
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder for embedded mode (ArcadeDB runs within the same JVM).
     */
    public static EmbeddedBuilder embeddedBuilder() {
        return new EmbeddedBuilder();
    }

    // ===== EmbeddingStore interface =====

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
        return addAll(embeddings, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(Collectors.toList());
        addAll(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("No embeddings to add to ArcadeDB");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");
        if (embeddedMode) {
            addAllEmbedded(ids, embeddings, embedded);
        } else {
            addAllRemote(ids, embeddings, embedded);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        if (embeddedMode) {
            embeddedDatabase.transaction(() -> {
                for (String id : ids) {
                    softDeleteById(id);
                }
            });
        } else {
            String idList =
                    ids.stream().map(id -> "'" + escapeString(id) + "'").collect(Collectors.joining(", "));
            remoteDatabase.command(
                    "sql", String.format("DELETE FROM `%s` WHERE %s IN [%s]", typeName, ID_PROPERTY, idList));
            rebuildRemoteIndex();
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        if (embeddedMode) {
            removeAllByFilterEmbedded(filter);
        } else {
            log.info("Number of embeddings before delete: {}", findAllRemote(1000).size());
            String whereClause = filterMapper.map(filter);
            String sql = String.format("DELETE FROM `%s` WHERE %s", typeName, whereClause);
            log.debug("Removing with filter: {}", sql);
            remoteDatabase.command("sql", sql);
            log.info("Number of embeddings after delete: {}", findAllRemote(1000).size());
            rebuildRemoteIndex();
        }
    }

    @Override
    public void removeAll() {
        if (embeddedMode) {
            String quotedTypeName = "`" + typeName + "`";
            embeddedDatabase.transaction(() -> embeddedDatabase.command(
                    "sql", "DELETE FROM " + quotedTypeName));
            // Rebuild the index to produce a clean HNSW graph with no tombstones.
            // Without this, stale edges left by deleted nodes corrupt connectivity
            // for subsequent insertions.
            embeddedDatabase.command("sql", "REBUILD INDEX `" + typeName + "[" + EMBEDDED_EMBEDDING + "]`");
            vectorIndex = findVectorIndex();
        } else {
            remoteDatabase.command("sql", String.format("DELETE FROM `%s`", typeName));
            rebuildRemoteIndex();
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (embeddedMode) {
            return searchEmbedded(request);
        } else {
            return searchRemote(request);
        }
    }

    @Override
    public void close() {
        if (embeddedMode && embeddedDatabase != null && embeddedDatabase.isOpen()) {
            embeddedDatabase.close();
        }
    }

    // ===== Remote mode implementation =====

    private void addAllRemote(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        log.debug("Inserting {} embeddings into ArcadeDB (remote mode)", ids.size());
        for (int i = 0; i < ids.size(); i++) {
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO `").append(typeName).append("` SET ");
            sql.append(ID_PROPERTY).append(" = '").append(escapeString(ids.get(i))).append("'");
            sql.append(", ").append(EMBEDDING_PROPERTY).append(" = ").append(embeddingToSql(embeddings.get(i)));

            if (embedded != null && embedded.get(i) != null) {
                TextSegment segment = embedded.get(i);
                if (segment.text() != null) {
                    sql.append(", ")
                            .append(TEXT_PROPERTY)
                            .append(" = '")
                            .append(escapeString(segment.text()))
                            .append("'");
                }
                for (Map.Entry<String, Object> entry : segment.metadata().toMap().entrySet()) {
                    sql.append(", ")
                            .append(metadataPrefix)
                            .append(entry.getKey())
                            .append(" = ")
                            .append(valueToSql(entry.getValue()));
                }
            }

            remoteDatabase.command("sql", sql.toString());
        }
    }

    private EmbeddingSearchResult<TextSegment> searchRemote(EmbeddingSearchRequest request) {
        Embedding queryEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        // Validate the filter eagerly so unsupported types throw even when the index is empty.
        if (filter != null) {
            validateFilter(filter);
        }

        // Fetch extra candidates to account for in-memory filtering reducing the result set.
        int fetchCount = filter != null ? maxResults * 5 : maxResults;

        String vectorSql = embeddingToSql(queryEmbedding);
        String indexName = typeName + "[" + EMBEDDING_PROPERTY + "]";
        String query = String.format(
                "SELECT *, `vector.neighbors`('%s', %s, %d) AS neighbors FROM `%s`",
                indexName, vectorSql, fetchCount, typeName);

        ResultSet resultSet;
        try {
            resultSet = remoteDatabase.query("sql", query);
        } catch (Exception e) {
            log.debug("Vector search returned error (index may be empty): {}", e.getMessage());
            return new EmbeddingSearchResult<>(List.of());
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        while (resultSet.hasNext() && matches.size() < maxResults) {
            Result doc = resultSet.next();
            String docId = doc.getProperty(ID_PROPERTY);
            if (docId == null) continue;
            Embedding embedding = resultToEmbedding(doc.getProperty(EMBEDDING_PROPERTY));
            if (embedding == null) continue;
            double score =
                    RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(queryEmbedding, embedding));
            if (score < minScore) continue;
            String text = doc.hasProperty(TEXT_PROPERTY) ? doc.getProperty(TEXT_PROPERTY) : null;
            Map<String, Object> metadataMap = extractMetadataFromResult(doc);
            if (filter != null && !matchesFilter(filter, metadataMap)) continue;
            matches.add(new EmbeddingMatch<>(score, docId, embedding, buildTextSegment(text, metadataMap)));
        }

        matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        return new EmbeddingSearchResult<>(matches);
    }

    private void rebuildRemoteIndex() {
        remoteDatabase.command("sql", "REBUILD INDEX *");
    }

    /**
     * Retrieves all stored embeddings using a SQL scan. Package-private for use in tests.
     */
    List<EmbeddingMatch<TextSegment>> findAllRemote(int maxResults) {
        String sql = String.format("SELECT * FROM `%s` LIMIT %d", typeName, maxResults);
        ResultSet resultSet = remoteDatabase.query("sql", sql);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        while (resultSet.hasNext()) {
            Result doc = resultSet.next();
            String docId = doc.getProperty(ID_PROPERTY);
            if (docId == null) continue;
            Embedding embedding = resultToEmbedding(doc.getProperty(EMBEDDING_PROPERTY));
            if (embedding == null) continue;
            String text = doc.hasProperty(TEXT_PROPERTY) ? doc.getProperty(TEXT_PROPERTY) : null;
            Map<String, Object> metadataMap = extractMetadataFromResult(doc);
            matches.add(new EmbeddingMatch<>(1.0, docId, embedding, buildTextSegment(text, metadataMap)));
        }
        log.info("findAll: SQL returned {} matches", matches.size());
        return matches;
    }

    private void initRemoteSchema(
            int dimension, String similarityFunction, int maxConnections, int beamWidth) {
        String script = String.format(
                """
                CREATE VERTEX TYPE `%s` IF NOT EXISTS;
                CREATE PROPERTY `%s`.`%s` IF NOT EXISTS STRING;
                CREATE PROPERTY `%s`.`%s` IF NOT EXISTS ARRAY_OF_FLOATS;
                CREATE PROPERTY `%s`.`%s` IF NOT EXISTS STRING;
                CREATE INDEX IF NOT EXISTS ON `%s` (`%s`) LSM_VECTOR
                  METADATA {
                    "dimensions": %d,
                    "similarity": "%s",
                    "maxConnections": %d,
                    "beamWidth": %d
                  };
                """,
                typeName,
                typeName, ID_PROPERTY,
                typeName, EMBEDDING_PROPERTY,
                typeName, TEXT_PROPERTY,
                typeName, EMBEDDING_PROPERTY,
                dimension, similarityFunction, maxConnections, beamWidth);
        log.debug("Initializing ArcadeDB schema (remote mode)");
        remoteDatabase.command("sqlscript", script);
    }

    private Map<String, Object> extractMetadataFromResult(Result doc) {
        Map<String, Object> metadata = new HashMap<>();
        for (String prop : doc.getPropertyNames()) {
            if (prop.startsWith(metadataPrefix)) {
                metadata.put(prop.substring(metadataPrefix.length()), doc.getProperty(prop));
            }
        }
        return metadata;
    }

    // ===== Embedded mode implementation =====

    private void addAllEmbedded(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        log.debug("Inserting {} embeddings into ArcadeDB (embedded mode)", ids.size());
        for (int i = 0; i < ids.size(); i++) {
            final int idx = i;
            final String id = ids.get(i);
            embeddedDatabase.transaction(() -> {
                // Soft-delete existing vertex with same id (upsert semantics)
                softDeleteById(id);

                MutableVertex vertex = embeddedDatabase.newVertex(typeName);
                vertex.set(EMBEDDED_ID, id);
                vertex.set(EMBEDDED_EMBEDDING, embeddingToFloatArray(embeddings.get(idx)));

                if (segments != null && segments.get(idx) != null) {
                    TextSegment segment = segments.get(idx);
                    vertex.set(EMBEDDED_TEXT, segment.text());

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

                // Saving the vertex automatically indexes the embedding via the LSM vector index
                vertex.save();
            });
        }
    }

    private EmbeddingSearchResult<TextSegment> searchEmbedded(EmbeddingSearchRequest request) {
        ensureNotNull(request.queryEmbedding(), "queryEmbedding");

        float[] queryVector = embeddingToFloatArray(request.queryEmbedding());
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        // Over-fetch to account for soft-deleted documents and in-memory filter reduction.
        int fetchSize = Math.max(maxResults * 4, maxResults + 100);
        List<Pair<RID, Float>> neighbors = vectorIndex.findNeighborsFromVector(queryVector, fetchSize);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (Pair<RID, Float> neighbor : neighbors) {
            if (matches.size() >= maxResults) break;
            // ArcadeDB returns (1 - cosine_similarity) / 2, so relevance score = 1 - rawDistance
            double rawDistance = neighbor.getSecond().doubleValue();
            double score = 1.0 - rawDistance;
            if (score >= minScore) {
                try {
                    Vertex vertex = neighbor.getFirst().getRecord(true).asVertex();
                    if (Boolean.TRUE.equals(vertex.get(PROPERTY_DELETED))) continue;
                    if (filter != null) {
                        Map<String, Object> metadata = extractMetadata(vertex, metadataPrefix);
                        if (!matchesFilter(filter, metadata)) continue;
                    }
                    matches.add(toEmbeddingMatch(vertex, score, metadataPrefix));
                } catch (Exception e) {
                    log.warn("Failed to load vertex {}: {}", neighbor.getFirst(), e.getMessage());
                }
            }
        }
        return new EmbeddingSearchResult<>(matches);
    }

    private void removeAllByFilterEmbedded(Filter filter) {
        String quotedTypeName = "`" + typeName + "`";
        embeddedDatabase.transaction(() -> {
            try (ResultSet rs = embeddedDatabase.query(
                    "sql",
                    "SELECT FROM " + quotedTypeName + " WHERE (" + PROPERTY_DELETED + " IS NULL OR "
                            + PROPERTY_DELETED + " != true)")) {
                while (rs.hasNext()) {
                    Result result = rs.next();
                    result.getVertex().ifPresent(v -> {
                        Map<String, Object> metadata = extractMetadata(v, metadataPrefix);
                        if (matchesFilter(filter, metadata)) {
                            v.modify().set(PROPERTY_DELETED, true).save();
                            vectorIndex.remove(new Object[] {null}, v);
                        }
                    });
                }
            }
        });
    }

    private void initEmbeddedSchema(EmbeddedBuilder builder) {
        embeddedDatabase.transaction(() -> {
            Schema schema = embeddedDatabase.getSchema();

            // Create or get the vertex type
            VertexType vertexType = schema.existsType(typeName)
                    ? (VertexType) schema.getType(typeName)
                    : schema.createVertexType(typeName, 1);

            if (!vertexType.existsProperty(EMBEDDED_ID)) {
                vertexType.createProperty(EMBEDDED_ID, Type.STRING);
            }
            if (!vertexType.existsProperty(EMBEDDED_EMBEDDING)) {
                vertexType.createProperty(EMBEDDED_EMBEDDING, Type.ARRAY_OF_FLOATS);
            }
            if (!vertexType.existsProperty(EMBEDDED_TEXT)) {
                vertexType.createProperty(EMBEDDED_TEXT, Type.STRING);
            }
            if (!vertexType.existsProperty(PROPERTY_DELETED)) {
                vertexType.createProperty(PROPERTY_DELETED, Type.BOOLEAN);
            }

            // Create unique index on id
            if (vertexType.getPolymorphicIndexByProperties(EMBEDDED_ID) == null) {
                schema.createTypeIndex(Schema.INDEX_TYPE.LSM_TREE, true, typeName, EMBEDDED_ID);
            }
        });

        // Check if vector index already exists
        vectorIndex = findVectorIndex();
        if (vectorIndex != null) {
            return;
        }

        // Create LSM vector index
        embeddedDatabase.transaction(() -> {
            embeddedDatabase
                    .getSchema()
                    .buildTypeIndex(typeName, new String[] {EMBEDDED_EMBEDDING})
                    .withLSMVectorType()
                    .withDimensions(builder.dimension)
                    .withSimilarity("COSINE")
                    .withMaxConnections(builder.maxConnections)
                    .withBeamWidth(builder.beamWidth)
                    .withIdProperty(EMBEDDED_ID)
                    .create();
        });

        vectorIndex = findVectorIndex();
    }

    private LSMVectorIndex findVectorIndex() {
        try {
            String indexName = typeName + "[" + EMBEDDED_EMBEDDING + "]";
            var index = embeddedDatabase.getSchema().getIndexByName(indexName);
            if (index instanceof TypeIndex typeIndex) {
                for (IndexInternal subIndex : typeIndex.getSubIndexes()) {
                    if (subIndex instanceof LSMVectorIndex lsmIndex) {
                        return lsmIndex;
                    }
                }
            } else if (index instanceof LSMVectorIndex lsmIndex) {
                return lsmIndex;
            }
        } catch (Exception e) {
            // Index doesn't exist yet
        }
        return null;
    }

    private void softDeleteById(String id) {
        String quotedTypeName = "`" + typeName + "`";
        try (ResultSet rs =
                embeddedDatabase.query("sql", "SELECT FROM " + quotedTypeName + " WHERE " + EMBEDDED_ID + " = ?", id)) {
            while (rs.hasNext()) {
                Result result = rs.next();
                result.getVertex().ifPresent(v -> {
                    v.modify().set(PROPERTY_DELETED, true).save();
                    // Also remove from the vector index so it's excluded from future searches
                    vectorIndex.remove(new Object[] {null}, v);
                });
            }
        }
    }

    // ===== Shared helpers =====

    private static Embedding resultToEmbedding(Object embObj) {
        if (embObj instanceof List<?> embList) {
            float[] vector = new float[embList.size()];
            for (int j = 0; j < embList.size(); j++) {
                vector[j] = ((Number) embList.get(j)).floatValue();
            }
            return new Embedding(vector);
        }
        return null;
    }

    private static TextSegment buildTextSegment(String text, Map<String, Object> metadataMap) {
        if (text != null) {
            return TextSegment.from(text, Metadata.from(metadataMap));
        } else if (!metadataMap.isEmpty()) {
            return TextSegment.from("", Metadata.from(metadataMap));
        }
        return null;
    }

    // ===== Filter evaluation =====

    /**
     * Validates that the filter only contains types supported by the remote mode, throwing
     * {@link UnsupportedOperationException} eagerly for unsupported filter types.
     * Not used in embedded mode, which supports all filter types including {@code ContainsString}.
     */
    private static void validateFilter(Filter filter) {
        if (filter instanceof And f) {
            validateFilter(f.left());
            validateFilter(f.right());
        } else if (filter instanceof Or f) {
            validateFilter(f.left());
            validateFilter(f.right());
        } else if (filter instanceof Not f) {
            validateFilter(f.expression());
        } else if (!(filter instanceof IsEqualTo
                || filter instanceof IsNotEqualTo
                || filter instanceof IsGreaterThan
                || filter instanceof IsGreaterThanOrEqualTo
                || filter instanceof IsLessThan
                || filter instanceof IsLessThanOrEqualTo
                || filter instanceof IsIn
                || filter instanceof IsNotIn
                || filter instanceof ContainsString)) {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    /**
     * Evaluates a {@link Filter} in-memory against a document's metadata map.
     * Supports all filter types including {@code ContainsString}.
     */
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
        throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
    }

    /**
     * Compares two values. Numbers are compared at Float precision when the expected value
     * is a {@link Float}, to match values stored as {@code 1.1f} with JSON {@code 1.1}.
     * Everything else falls back to string comparison.
     */
    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            if (b instanceof Float) {
                return Float.compare(((Number) a).floatValue(), ((Number) b).floatValue()) == 0;
            }
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        return Objects.equals(a == null ? "" : a.toString(), b == null ? "" : b.toString());
    }

    private static int compareValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            if (b instanceof Float) {
                return Float.compare(((Number) a).floatValue(), ((Number) b).floatValue());
            }
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        return a.toString().compareTo(b.toString());
    }

    // ===== SQL helpers =====

    private static String embeddingToSql(Embedding embedding) {
        float[] vector = embedding.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String valueToSql(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "'" + escapeString(value.toString()) + "'";
        } else if (value instanceof Long l) {
            // ArcadeDB's SQL parser can't handle Long.MIN_VALUE directly because
            // -(9223372036854775808) overflows. Express it as (MIN+1)-1 as a workaround.
            if (l == Long.MIN_VALUE) {
                return "(-9223372036854775807 - 1)";
            }
            return l.toString();
        } else if (value instanceof Float f) {
            // Use decimal notation to avoid scientific notation parsing issues
            return String.valueOf(f.doubleValue());
        } else if (value instanceof Double d) {
            return String.valueOf(d);
        } else {
            return value.toString();
        }
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }

    // ===== Builders =====

    /**
     * Builder for remote mode. Connects to an ArcadeDB server via HTTP.
     *
     * <p>Example:
     * <pre>{@code
     * ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.builder()
     *     .host("localhost")
     *     .port(2480)
     *     .databaseName("mydb")
     *     .username("root")
     *     .password("password")
     *     .dimension(384)
     *     .createDatabase(true)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private String host;
        private int port = DEFAULT_PORT;
        private String databaseName;
        private String username;
        private String password;
        private String typeName = DEFAULT_TYPE_NAME;
        private int dimension;
        private String similarityFunction = DEFAULT_SIMILARITY;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private int beamWidth = DEFAULT_BEAM_WIDTH;
        private boolean createDatabase = false;
        private String metadataPrefix = REMOTE_DEFAULT_METADATA_PREFIX;

        /**
         * @param host ArcadeDB server hostname. Required.
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port ArcadeDB server HTTP port. Default: 2480.
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * @param databaseName Name of the ArcadeDB database. Required.
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * @param username ArcadeDB server username. Required.
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * @param password ArcadeDB server password. Required.
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param typeName The vertex type name for storing embeddings. Default: "EmbeddingDocument".
         */
        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        /**
         * @param dimension The dimensionality of the embedding vectors. Required.
         */
        public Builder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param similarityFunction The similarity function for vector comparison. Default: "COSINE".
         *     Options: "COSINE", "EUCLIDEAN", "SQUARED_EUCLIDEAN".
         */
        public Builder similarityFunction(String similarityFunction) {
            this.similarityFunction = similarityFunction;
            return this;
        }

        /**
         * @param maxConnections Maximum number of connections per node in the HNSW graph. Default: 16.
         */
        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * @param beamWidth Beam width for HNSW index search. Default: 100.
         */
        public Builder beamWidth(int beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        /**
         * @param createDatabase Whether to create the database if it does not exist. Default: false.
         */
        public Builder createDatabase(boolean createDatabase) {
            this.createDatabase = createDatabase;
            return this;
        }

        /**
         * @param metadataPrefix Prefix for metadata properties stored on the vertex. Default: "meta_".
         */
        public Builder metadataPrefix(String metadataPrefix) {
            this.metadataPrefix = metadataPrefix;
            return this;
        }

        public ArcadeDBEmbeddingStore build() {
            ensureNotBlank(host, "host");
            ensureNotBlank(databaseName, "databaseName");
            ensureNotBlank(username, "username");
            ensureNotNull(password, "password");
            ensureTrue(dimension > 0, "dimension must be positive");

            if (createDatabase) {
                RemoteServer server = new RemoteServer(host, port, username, password);
                if (!server.exists(databaseName)) {
                    server.create(databaseName);
                }
            }

            RemoteDatabase database = new RemoteDatabase(host, port, databaseName, username, password);
            return new ArcadeDBEmbeddingStore(
                    database,
                    getOrDefault(typeName, DEFAULT_TYPE_NAME),
                    dimension,
                    getOrDefault(similarityFunction, DEFAULT_SIMILARITY),
                    maxConnections,
                    beamWidth,
                    getOrDefault(metadataPrefix, REMOTE_DEFAULT_METADATA_PREFIX));
        }
    }

    /**
     * Builder for embedded mode. ArcadeDB runs within the same JVM.
     *
     * <p>Supports all filter types including {@code ContainsString}. Uses soft-delete to preserve
     * HNSW graph connectivity.
     *
     * <p>Example:
     * <pre>{@code
     * ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.embeddedBuilder()
     *     .databasePath("/tmp/mydb")
     *     .dimension(384)
     *     .build();
     * }</pre>
     *
     * <p>Remember to call {@link ArcadeDBEmbeddingStore#close()} when done to release resources.
     */
    public static class EmbeddedBuilder {
        private String databasePath;
        private Database database;
        private String typeName = DEFAULT_TYPE_NAME;
        private String metadataPrefix = EMBEDDED_DEFAULT_METADATA_PREFIX;
        int dimension = 384;
        int maxConnections = DEFAULT_MAX_CONNECTIONS;
        int beamWidth = DEFAULT_BEAM_WIDTH;

        /**
         * @param databasePath Path to the embedded ArcadeDB database directory. Required unless
         *     {@link #database(Database)} is provided.
         */
        public EmbeddedBuilder databasePath(String databasePath) {
            this.databasePath = databasePath;
            return this;
        }

        /**
         * @param database An existing {@link Database} instance to use instead of creating one.
         */
        public EmbeddedBuilder database(Database database) {
            this.database = database;
            return this;
        }

        /**
         * @param typeName The vertex type name for storing embeddings. Default: "EmbeddingDocument".
         */
        public EmbeddedBuilder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        /**
         * @param metadataPrefix Prefix for metadata properties stored on the vertex. Default: ""
         *     (no prefix).
         */
        public EmbeddedBuilder metadataPrefix(String metadataPrefix) {
            this.metadataPrefix = metadataPrefix;
            return this;
        }

        /**
         * @param dimension The dimensionality of the embedding vectors. Default: 384.
         */
        public EmbeddedBuilder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param maxConnections Maximum connections per node in the HNSW graph. Default: 16.
         */
        public EmbeddedBuilder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * @param beamWidth Search beam width. Higher values improve recall. Default: 100.
         */
        public EmbeddedBuilder beamWidth(int beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        public ArcadeDBEmbeddingStore build() {
            ensureTrue(dimension > 0, "dimension must be positive");
            return new ArcadeDBEmbeddingStore(this);
        }
    }
}
