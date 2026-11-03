package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.PROPERTY_DELETED;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.embeddingToFloatArray;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.extractMetadata;
import static dev.langchain4j.community.store.embedding.arcadedb.ArcadeDBEmbeddingUtils.toEmbeddingMatch;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
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
import com.arcadedb.schema.TypeLSMVectorIndexBuilder;
import com.arcadedb.schema.VertexType;
import com.arcadedb.utility.Pair;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
    /** Rows per request when adding embeddings in remote mode. */
    private static final int DEFAULT_REMOTE_BATCH_SIZE = 500;
    /** Rows per transaction when adding embeddings in embedded mode. */
    private static final int DEFAULT_EMBEDDED_BATCH_SIZE = 1000;

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

    // Embedded mode index parameters (needed to recreate the index after removeAll)
    private final int embeddedDimension;
    private final int embeddedMaxConnections;
    private final int embeddedBeamWidth;
    // Rows per request in remote addAll; 0 in embedded mode.
    private final int remoteBatchSize;
    // Rows per transaction in embedded addAll; 0 in remote mode.
    private final int embeddedBatchSize;
    // Whether hybrid query text is passed to the full-text index as Lucene syntax (true) or escaped first (false).
    private final boolean rawFullTextQuery;
    // Fusion and full-text index settings for hybrid search.
    private final ArcadeDBHybridSettings hybrid;
    // Search beam width of the vector index per query; null for the defaults (see dense search in each mode).
    private final Integer efSearch;
    // Similarity function and quantization of the vector index (as configured; an existing index keeps its own).
    private final ArcadeDBVectorSimilarity similarity;
    private final ArcadeDBVectorQuantization quantization;
    // How searches with a metadata filter are executed.
    private final ArcadeDBFilterStrategy filterStrategy;

    // ===== Remote mode constructor =====

    private ArcadeDBEmbeddingStore(
            RemoteDatabase remoteDatabase,
            String typeName,
            int dimension,
            String similarityFunction,
            int maxConnections,
            int beamWidth,
            String metadataPrefix,
            int batchSize,
            boolean rawFullTextQuery,
            ArcadeDBHybridSettings hybrid,
            Integer efSearch,
            ArcadeDBVectorQuantization quantization,
            ArcadeDBFilterStrategy filterStrategy) {
        this.embeddedMode = false;
        this.remoteBatchSize = batchSize;
        this.embeddedBatchSize = 0;
        this.rawFullTextQuery = rawFullTextQuery;
        this.hybrid = hybrid;
        this.efSearch = efSearch;
        this.similarity = ArcadeDBVectorSimilarity.of(similarityFunction);
        this.quantization = quantization;
        this.filterStrategy = filterStrategy;
        this.remoteDatabase = ensureNotNull(remoteDatabase, "database");
        this.embeddedDatabase = null;
        this.typeName = ensureNotBlank(typeName, "typeName");
        this.metadataPrefix = metadataPrefix;
        this.filterMapper = new ArcadeDBMetadataFilterMapper(metadataPrefix, true);
        this.embeddedDimension = 0;
        this.embeddedMaxConnections = 0;
        this.embeddedBeamWidth = 0;
        initRemoteSchema(dimension, similarity.name(), maxConnections, beamWidth);
    }

    // ===== Embedded mode constructor =====

    private ArcadeDBEmbeddingStore(EmbeddedBuilder builder) {
        this.embeddedMode = true;
        this.remoteBatchSize = 0;
        this.embeddedBatchSize = builder.batchSize;
        this.rawFullTextQuery = builder.rawFullTextQuery;
        this.hybrid = builder.hybrid.copy();
        this.efSearch = builder.efSearch;
        this.similarity = ArcadeDBVectorSimilarity.of(getOrDefault(builder.similarityFunction, DEFAULT_SIMILARITY));
        this.quantization = builder.quantization;
        this.filterStrategy = builder.filterStrategy;
        this.remoteDatabase = null;
        this.filterMapper = new ArcadeDBMetadataFilterMapper(builder.metadataPrefix, false);
        this.typeName = builder.typeName;
        this.metadataPrefix = builder.metadataPrefix;
        this.embeddedDimension = builder.dimension;
        this.embeddedMaxConnections = builder.maxConnections;
        this.embeddedBeamWidth = builder.beamWidth;
        if (builder.database != null) {
            this.embeddedDatabase = builder.database;
        } else {
            ensureNotBlank(builder.databasePath, "databasePath");
            try (DatabaseFactory factory = new DatabaseFactory(builder.databasePath)) {
                this.embeddedDatabase = factory.exists() ? factory.open() : factory.create();
            }
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
        if (isNullOrEmpty(ids)) {
            return;
        }
        if (embeddedMode) {
            embeddedDatabase.transaction(() -> {
                for (String id : ids) {
                    softDeleteById(id);
                }
            });
        } else {
            remoteDatabase.command(
                    "sql",
                    String.format("DELETE FROM `%s` WHERE `%s` IN :ids", typeName, ID_PROPERTY),
                    Map.of("ids", List.copyOf(ids)));
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        if (embeddedMode) {
            removeAllByFilterEmbedded(filter);
        } else {
            log.info(
                    "Number of embeddings before delete: {}",
                    findAllRemote(1000).size());
            List<Object> parameters = new ArrayList<>();
            String whereClause = filterMapper.map(filter, parameters);
            String sql = String.format("DELETE FROM `%s` WHERE %s", typeName, whereClause);
            log.debug("Removing with filter: {}", sql);
            remoteDatabase.command("sql", sql, parameters.toArray());
            log.info(
                    "Number of embeddings after delete: {}", findAllRemote(1000).size());
        }
    }

    @Override
    public void removeAll() {
        if (embeddedMode) {
            String quotedTypeName = "`" + typeName + "`";
            // Delete all records and drop+recreate the vector index to guarantee a clean HNSW graph.
            // REBUILD INDEX alone does not reset the entry point, so subsequent insertions of
            // identical vectors can result in a disconnected graph that findNeighborsFromVector
            // cannot fully traverse. Dropping and recreating the index ensures a fresh graph state.
            embeddedDatabase.transaction(() -> embeddedDatabase.command("sql", "DELETE FROM " + quotedTypeName));
            String indexName = typeName + "[" + EMBEDDED_EMBEDDING + "]";
            embeddedDatabase.transaction(() -> embeddedDatabase.getSchema().dropIndex(indexName));
            embeddedDatabase.transaction(
                    () -> createEmbeddedVectorIndex(embeddedDimension, embeddedMaxConnections, embeddedBeamWidth));
            vectorIndex = findVectorIndex();
        } else {
            remoteDatabase.command("sql", String.format("DELETE FROM `%s`", typeName));
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
        // One request and one transaction per batch: a SQL script of parameterized DELETE + INSERT pairs (upsert by id,
        // as in embedded mode; the id index keeps the DELETE cheap). Values are bound as parameters; inlined into the
        // SQL
        // text, a line break in a text or metadata value was a syntax error.
        for (int from = 0; from < ids.size(); from += remoteBatchSize) {
            int to = Math.min(from + remoteBatchSize, ids.size());
            StringBuilder script = new StringBuilder("BEGIN;\n");
            Map<String, Object> parameters = new HashMap<>();
            for (int i = from; i < to; i++) {
                appendRemoteInsert(
                        script,
                        parameters,
                        i - from,
                        ids.get(i),
                        embeddings.get(i),
                        embedded == null ? null : embedded.get(i));
            }
            script.append("COMMIT;");
            remoteDatabase.command("sqlscript", script.toString(), parameters);
        }
    }

    private void appendRemoteInsert(
            StringBuilder script,
            Map<String, Object> parameters,
            int row,
            String id,
            Embedding embedding,
            TextSegment segment) {
        script.append("DELETE FROM `")
                .append(typeName)
                .append("` WHERE `")
                .append(ID_PROPERTY)
                .append("` = :id");
        script.append(row).append(";\n");
        script.append("INSERT INTO `")
                .append(typeName)
                .append("` SET `")
                .append(ID_PROPERTY)
                .append("` = :id");
        script.append(row);
        parameters.put("id" + row, id);
        script.append(", `").append(EMBEDDING_PROPERTY).append("` = :embedding").append(row);
        parameters.put("embedding" + row, embedding.vector());
        if (segment != null) {
            if (segment.text() != null) {
                script.append(", `").append(TEXT_PROPERTY).append("` = :text").append(row);
                parameters.put("text" + row, segment.text());
            }
            int m = 0;
            for (Map.Entry<String, Object> entry : segment.metadata().toMap().entrySet()) {
                String name = "m" + row + "_" + m++;
                script.append(", `")
                        .append(metadataPrefix)
                        .append(entry.getKey())
                        .append("` = :")
                        .append(name);
                parameters.put(name, toParameter(entry.getValue()));
            }
        }
        script.append(";\n");
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

        boolean isHybrid = request.query() != null && !request.query().trim().isEmpty();
        // Remote mode filters in SQL whenever it can (ADAPTIVE and PRE_FILTER): one query on the server, measured
        // faster than fetching 5x the results and filtering them here, at every selectivity.
        boolean filterInSql = filter != null
                && filterStrategy != ArcadeDBFilterStrategy.POST_FILTER
                && ArcadeDBMetadataFilterMapper.supports(filter);

        String query;
        Map<String, Object> parameters = Map.of();
        if (isHybrid) {
            Map<String, Object> named = new LinkedHashMap<>();
            named.put("query", fullTextQuery(request.query()));
            String where = filterInSql ? filterMapper.mapNamed(filter, named) : null;
            query = withoutNestedRecord(buildHybridSearchQuery(
                    queryEmbedding,
                    EMBEDDING_PROPERTY,
                    TEXT_PROPERTY,
                    maxResults,
                    filter != null && !filterInSql,
                    false,
                    where));
            parameters = named;
        } else if (filterInSql) {
            // The filter selects the allowed records on the server; the search returns the nearest of them.
            Map<String, Object> named = new LinkedHashMap<>();
            String where = filterMapper.mapNamed(filter, named);
            query = withoutNestedRecord(String.format(
                    "SELECT expand(`vector.neighbors`('%s', %s, %d, { efSearch: %d, filter: (SELECT @rid FROM `%s`"
                            + " WHERE %s).@rid }))",
                    typeName + "[" + EMBEDDING_PROPERTY + "]",
                    embeddingToSql(queryEmbedding),
                    maxResults,
                    remoteEfSearch(maxResults, maxResults),
                    typeName,
                    where));
            parameters = named;
        } else {
            int fetchCount = filter != null ? maxResults * 5 : maxResults;
            String vectorSql = embeddingToSql(queryEmbedding);
            String indexName = typeName + "[" + EMBEDDING_PROPERTY + "]";
            // Expand the neighbours themselves. Projecting vector.neighbors() per row of the type instead runs one
            // ANN search for every record and returns the records in scan order, not by similarity.
            // Only fetchCount rows cross the network; the beam (efSearch) is as wide as embedded search's.
            query = withoutNestedRecord(String.format(
                    "SELECT expand(`vector.neighbors`('%s', %s, %d, { efSearch: %d }))",
                    indexName, vectorSql, fetchCount, remoteEfSearch(maxResults, fetchCount)));
        }

        ResultSet resultSet;
        try {
            resultSet = remoteDatabase.query("sql", query, parameters);
        } catch (Exception e) {
            if (isHybrid) {
                log.warn("Hybrid search failed, falling back to dense search: {}", e.getMessage());
                return searchRemote(withoutQuery(request));
            }
            log.warn("Vector search returned error: {}", e.getMessage(), e);
            return new EmbeddingSearchResult<>(List.of());
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        while (resultSet.hasNext() && matches.size() < maxResults) {
            Result doc = resultSet.next();
            String docId = doc.getProperty(ID_PROPERTY);
            if (docId == null) continue;
            Embedding embedding = resultToEmbedding(doc.getProperty(EMBEDDING_PROPERTY));
            if (embedding == null) continue;
            double score = similarity.relevance(queryEmbedding, embedding);
            if (score < minScore) continue;
            String text = doc.hasProperty(TEXT_PROPERTY) ? doc.getProperty(TEXT_PROPERTY) : null;
            Map<String, Object> metadataMap = extractMetadataFromResult(doc);
            if (filter != null && !filterInSql && !matchesFilter(filter, metadataMap)) continue;
            matches.add(new EmbeddingMatch<>(score, docId, embedding, buildTextSegment(text, metadataMap)));
        }

        // vector.fuse() has already ordered hybrid results by their fused RRF score. Re-sorting
        // by cosine similarity would discard the lexical contribution to that ranking.
        if (!isHybrid) {
            matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        }
        return new EmbeddingSearchResult<>(matches);
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

    private void initRemoteSchema(int dimension, String similarityFunction, int maxConnections, int beamWidth) {
        String script = String.format(
                """
                CREATE VERTEX TYPE `%s` IF NOT EXISTS;
                CREATE PROPERTY `%s`.`%s` IF NOT EXISTS STRING;
                CREATE PROPERTY `%s`.`%s` IF NOT EXISTS ARRAY_OF_FLOATS;
                CREATE PROPERTY `%s`.`%s` IF NOT EXISTS STRING;
                CREATE INDEX IF NOT EXISTS ON `%s` (`%s`) NOTUNIQUE;
                """,
                typeName,
                typeName,
                ID_PROPERTY,
                typeName,
                EMBEDDING_PROPERTY,
                typeName,
                TEXT_PROPERTY,
                typeName,
                ID_PROPERTY);
        log.debug("Initializing ArcadeDB schema (remote mode)");
        remoteDatabase.command("sqlscript", script);

        // Like embedded mode, an existing vector index is kept as it is. Since ArcadeDB 26.8.1, CREATE INDEX IF NOT
        // EXISTS fails when the existing index has a different configuration (e.g. maxConnections), so it is only
        // sent when the index is missing.
        // The full-text index carries settings too (analyzer, BM25), so the same rule applies.
        if (!remoteIndexExists(typeName + "[" + TEXT_PROPERTY + "]")) {
            remoteDatabase.command(
                    "sql",
                    String.format(
                            "CREATE INDEX IF NOT EXISTS ON `%s` (`%s`) FULL_TEXT%s",
                            typeName, TEXT_PROPERTY, hybrid.fullTextIndexMetadata()));
        }
        if (!remoteIndexExists(typeName + "[" + EMBEDDING_PROPERTY + "]")) {
            remoteDatabase.command(
                    "sql",
                    String.format(
                            """
                            CREATE INDEX IF NOT EXISTS ON `%s` (`%s`) LSM_VECTOR
                              METADATA {
                                "dimensions": %d,
                                "similarity": "%s",
                                "maxConnections": %d,
                                "beamWidth": %d%s
                              }
                            """,
                            typeName,
                            EMBEDDING_PROPERTY,
                            dimension,
                            similarityFunction,
                            maxConnections,
                            beamWidth,
                            quantization == ArcadeDBVectorQuantization.NONE
                                    ? ""
                                    : ",\n    \"quantization\": \"" + quantization.name() + "\""));
        }
    }

    private boolean remoteIndexExists(String indexName) {
        try (ResultSet resultSet =
                remoteDatabase.query("sql", "SELECT name FROM schema:indexes WHERE name = ?", indexName)) {
            return resultSet.hasNext();
        }
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
        // One transaction per batch rather than per embedding.
        for (int from = 0; from < ids.size(); from += embeddedBatchSize) {
            int batchStart = from;
            int batchEnd = Math.min(from + embeddedBatchSize, ids.size());
            embeddedDatabase.transaction(() -> {
                for (int i = batchStart; i < batchEnd; i++) {
                    insertEmbedded(ids.get(i), embeddings.get(i), segments == null ? null : segments.get(i));
                }
            });
        }
    }

    private void insertEmbedded(String id, Embedding embedding, TextSegment segment) {
        // Soft-delete existing vertex with same id (upsert semantics)
        softDeleteById(id);

        MutableVertex vertex = embeddedDatabase.newVertex(typeName);
        vertex.set(EMBEDDED_ID, id);
        vertex.set(EMBEDDED_EMBEDDING, embeddingToFloatArray(embedding));

        if (segment != null) {
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
    }

    public EmbeddingSearchResult<TextSegment> searchEmbedded(EmbeddingSearchRequest request) {
        ensureNotNull(request, "request");
        ensureNotNull(request.queryEmbedding(), "queryEmbedding");

        Embedding queryEmbedding = request.queryEmbedding();
        float[] queryVector = embeddingToFloatArray(queryEmbedding);
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        boolean isHybrid = request.query() != null && !request.query().trim().isEmpty();
        boolean sqlFilterPossible = filter != null
                && filterStrategy != ArcadeDBFilterStrategy.POST_FILTER
                && ArcadeDBMetadataFilterMapper.supports(filter);
        boolean preFilter = sqlFilterPossible && filterStrategy == ArcadeDBFilterStrategy.PRE_FILTER;
        if (isHybrid) {
            return searchEmbeddedHybrid(request, preFilter);
        }

        int fetchSize = Math.max(maxResults * 4, maxResults + 100);
        if (preFilter) {
            return searchEmbeddedPreFiltered(request, fetchSize);
        }
        List<Pair<RID, Float>> neighbors = efSearch != null
                ? vectorIndex.findNeighborsFromVector(queryVector, fetchSize, Math.max(efSearch, fetchSize))
                : vectorIndex.findNeighborsFromVector(queryVector, fetchSize);

        // When the graph search reaches fewer vectors than requested and available (e.g. identical vectors leave
        // parts of the graph unreachable), ArcadeDB completes the answer with a brute-force scan itself.
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        boolean rejectedByFilter = false;
        for (Pair<RID, Float> neighbor : neighbors) {
            RID rid = neighbor.getFirst();
            if (matches.size() >= maxResults) continue;
            // ArcadeDB's cosine distance is 1 - cosine; for the other functions the score is computed from the vector.
            double score = similarity == ArcadeDBVectorSimilarity.COSINE
                    ? 1.0 - neighbor.getSecond().doubleValue() / 2.0
                    : Double.NaN;
            if (score < minScore) continue;
            try {
                Vertex vertex = rid.getRecord(true).asVertex();
                if (Boolean.TRUE.equals(vertex.get(PROPERTY_DELETED))) continue;
                if (Double.isNaN(score)) {
                    Embedding embedding = resultToEmbedding(vertex.get(EMBEDDED_EMBEDDING));
                    if (embedding == null) continue;
                    score = similarity.relevance(queryEmbedding, embedding);
                    if (score < minScore) continue;
                }
                if (filter != null) {
                    Map<String, Object> metadata = extractMetadata(vertex, metadataPrefix);
                    if (!matchesFilter(filter, metadata)) {
                        rejectedByFilter = true;
                        continue;
                    }
                }
                matches.add(toEmbeddingMatch(vertex, score, metadataPrefix));
            } catch (Exception e) {
                log.warn("Failed to load vertex {}: {}", rid, e.getMessage());
            }
        }

        // ADAPTIVE: the filter rejected candidates and too few were left, while the index has more of them: search
        // again among the records the filter allows.
        if (sqlFilterPossible && rejectedByFilter && matches.size() < maxResults && neighbors.size() >= fetchSize) {
            return searchEmbeddedPreFiltered(request, fetchSize);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    /**
     * Dense search among the records the filter allows: the filter selects them in SQL, and the vector index searches
     * only those (exhaustively when there are few). Embedded mode's {@link ArcadeDBFilterStrategy#PRE_FILTER} path, and
     * the fallback of {@link ArcadeDBFilterStrategy#ADAPTIVE}.
     */
    private EmbeddingSearchResult<TextSegment> searchEmbeddedPreFiltered(
            EmbeddingSearchRequest request, int fetchSize) {
        Embedding queryEmbedding = request.queryEmbedding();
        Map<String, Object> named = new LinkedHashMap<>();
        String where = filterMapper.mapNamed(request.filter(), named);
        Set<RID> allowed = new HashSet<>();
        // No deleted check here: soft-deleted records are removed from the vector index (and skipped below), and the
        // check made this scan about 6x slower.
        String sql = String.format("SELECT @rid AS rid FROM `%s` WHERE %s", typeName, where);
        try (ResultSet rs = embeddedDatabase.query("sql", sql, named)) {
            while (rs.hasNext()) {
                Object rid = rs.next().getProperty("rid");
                if (rid instanceof com.arcadedb.database.Identifiable id) {
                    allowed.add(id.getIdentity());
                }
            }
        }
        if (allowed.isEmpty()) {
            return new EmbeddingSearchResult<>(List.of());
        }
        float[] queryVector = embeddingToFloatArray(queryEmbedding);
        List<Pair<RID, Float>> neighbors = efSearch != null
                ? vectorIndex.findNeighborsFromVector(queryVector, fetchSize, Math.max(efSearch, fetchSize), allowed)
                : vectorIndex.findNeighborsFromVector(queryVector, fetchSize, allowed);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (Pair<RID, Float> neighbor : neighbors) {
            if (matches.size() >= request.maxResults()) break;
            try {
                Vertex vertex = neighbor.getFirst().getRecord(true).asVertex();
                if (Boolean.TRUE.equals(vertex.get(PROPERTY_DELETED))) continue;
                Embedding embedding = resultToEmbedding(vertex.get(EMBEDDED_EMBEDDING));
                if (embedding == null) continue;
                double score = similarity.relevance(queryEmbedding, embedding);
                if (score >= request.minScore()) {
                    matches.add(toEmbeddingMatch(vertex, score, metadataPrefix));
                }
            } catch (Exception e) {
                log.warn("Failed to load vertex {}: {}", neighbor.getFirst(), e.getMessage());
            }
        }
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        return new EmbeddingSearchResult<>(matches);
    }

    private EmbeddingSearchResult<TextSegment> searchEmbeddedHybrid(
            EmbeddingSearchRequest request, boolean filterInSql) {
        Embedding queryEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        Map<String, Object> named = new LinkedHashMap<>();
        named.put("query", fullTextQuery(request.query()));
        String where = filterInSql ? filterMapper.mapNamed(filter, named) : null;
        String query = buildHybridSearchQuery(
                queryEmbedding,
                EMBEDDED_EMBEDDING,
                EMBEDDED_TEXT,
                maxResults,
                filter != null && !filterInSql,
                true,
                where);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        boolean rejectedByFilter = false;
        try (ResultSet rs = embeddedDatabase.query("sql", query, named)) {
            while (rs.hasNext() && matches.size() < maxResults) {
                Result result = rs.next();
                Vertex vertex = null;
                if (result.isVertex()) {
                    vertex = result.getVertex().get();
                } else if (result.hasProperty("record")) {
                    Object rec = result.getProperty("record");
                    if (rec instanceof Vertex v) {
                        vertex = v;
                    } else if (rec instanceof com.arcadedb.database.Identifiable id) {
                        vertex = (Vertex) embeddedDatabase.lookupByRID(id.getIdentity(), true);
                    }
                } else if (result.hasProperty("@rid")) {
                    Object ridProp = result.getProperty("@rid");
                    if (ridProp instanceof com.arcadedb.database.RID r) {
                        vertex = (Vertex) embeddedDatabase.lookupByRID(r, true);
                    } else if (ridProp instanceof com.arcadedb.database.Identifiable id) {
                        vertex = (Vertex) embeddedDatabase.lookupByRID(id.getIdentity(), true);
                    }
                }

                if (vertex == null) {
                    continue;
                }

                if (Boolean.TRUE.equals(vertex.get(PROPERTY_DELETED))) {
                    continue;
                }
                if (filter != null && !filterInSql) {
                    Map<String, Object> metadata = extractMetadata(vertex, metadataPrefix);
                    if (!matchesFilter(filter, metadata)) {
                        rejectedByFilter = true;
                        continue;
                    }
                }
                Embedding embedding = resultToEmbedding(vertex.get(EMBEDDED_EMBEDDING));
                if (embedding == null) {
                    continue;
                }
                double score = similarity.relevance(queryEmbedding, embedding);
                if (score >= minScore) {
                    matches.add(toEmbeddingMatch(vertex, score, metadataPrefix));
                }
            }
        } catch (Exception e) {
            log.warn("Hybrid search failed, falling back to dense search: {}", e.getMessage());
            return searchEmbedded(withoutQuery(request));
        }

        // ADAPTIVE: too few fused candidates passed the filter; fuse again with the filter applied to both sources.
        if (!filterInSql
                && rejectedByFilter
                && matches.size() < maxResults
                && filterStrategy == ArcadeDBFilterStrategy.ADAPTIVE
                && ArcadeDBMetadataFilterMapper.supports(filter)) {
            return searchEmbeddedHybrid(request, true);
        }
        return new EmbeddingSearchResult<>(matches);
    }

    private void removeAllByFilterEmbedded(Filter filter) {
        String quotedTypeName = "`" + typeName + "`";
        embeddedDatabase.transaction(() -> {
            try (ResultSet rs = embeddedDatabase.query(
                    "sql",
                    "SELECT FROM " + quotedTypeName + " WHERE (" + PROPERTY_DELETED + " IS NULL OR " + PROPERTY_DELETED
                            + " != true)")) {
                while (rs.hasNext()) {
                    Result result = rs.next();
                    result.getVertex().ifPresent(v -> {
                        Map<String, Object> metadata = extractMetadata(v, metadataPrefix);
                        if (matchesFilter(filter, metadata)) {
                            softDelete(v);
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
                    : schema.createVertexType(typeName);

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

        // Create the full-text index on the text property (with the analyzer / BM25 settings); an existing one is kept.
        String ftIndexName = typeName + "[" + EMBEDDED_TEXT + "]";
        if (!embeddedDatabase.getSchema().existsIndex(ftIndexName)) {
            embeddedDatabase.command(
                    "sql",
                    String.format(
                            "CREATE INDEX IF NOT EXISTS ON `%s` (`%s`) FULL_TEXT%s",
                            typeName, EMBEDDED_TEXT, hybrid.fullTextIndexMetadata()));
        }

        // Check if vector index already exists
        vectorIndex = findVectorIndex();
        if (vectorIndex != null) {
            return;
        }

        // Create LSM vector index
        embeddedDatabase.transaction(() -> {
            createEmbeddedVectorIndex(builder.dimension, builder.maxConnections, builder.beamWidth);
        });

        vectorIndex = findVectorIndex();
    }

    private void createEmbeddedVectorIndex(int dimension, int maxConnections, int beamWidth) {
        TypeLSMVectorIndexBuilder index = embeddedDatabase
                .getSchema()
                .buildTypeIndex(typeName, new String[] {EMBEDDED_EMBEDDING})
                .withLSMVectorType()
                .withDimensions(dimension)
                .withSimilarity(similarity.name())
                .withMaxConnections(maxConnections)
                .withBeamWidth(beamWidth)
                .withIdProperty(EMBEDDED_ID);
        if (quantization != ArcadeDBVectorQuantization.NONE) {
            index.withQuantization(quantization.name());
        }
        index.create();
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
                result.getVertex().ifPresent(this::softDelete);
            }
        }
    }

    /**
     * Marks the vertex deleted (it stays in place to preserve HNSW graph connectivity), removes it from the vector index
     * and releases its id: the id has a unique index, so a soft-deleted vertex that kept it made the id unusable for good
     * (upsert and add-after-remove failed with a DuplicatedKeyException). A vertex soft-deleted before ids were released
     * only has its id released.
     */
    private void softDelete(Vertex vertex) {
        boolean alreadyDeleted = Boolean.TRUE.equals(vertex.get(PROPERTY_DELETED));
        MutableVertex modified = vertex.modify();
        modified.set(PROPERTY_DELETED, true);
        modified.remove(EMBEDDED_ID);
        modified.save();
        if (!alreadyDeleted) {
            // Also remove from the vector index so it's excluded from future searches
            vectorIndex.remove(new Object[] {null}, vertex);
        }
    }

    // ===== Shared helpers =====

    // The filter option of vector.neighbors takes RIDs: the allow-list subquery is projected to them with .@rid
    // (26.10.2 also accepts the subquery's rows, 26.9.1 rejects them).

    /**
     * Remote dense search returns only the rows it needs and widens the search beam instead: the default beam for
     * {@code k} results is about {@code 2k}, so this matches embedded search, which asks for
     * {@code max(4k, k + 100)} neighbours. Measured on 100k NQ passages: ANN recall@10 0.970 with the default beam,
     * 0.992 with this one, at ~1 ms more per query. An explicit {@link Builder#efSearch(Integer)} replaces it.
     */
    private int remoteEfSearch(int maxResults, int fetchCount) {
        int beam = efSearch != null ? efSearch : 2 * Math.max(maxResults * 4, maxResults + 100);
        return Math.max(beam, fetchCount);
    }

    /**
     * Each row of {@code expand(vector.neighbors(...))} and {@code expand(vector.fuse(...))} carries the record's
     * properties and, again, the whole record under {@code record}; over HTTP that doubled every response (embedding
     * and text twice). This projection keeps the properties, {@code distance} / {@code score} and the order.
     */
    private static String withoutNestedRecord(String query) {
        return "SELECT *, !record FROM (" + query + ")";
    }

    /**
     * Builds the hybrid (dense + full-text) query; the full-text query text is the parameter {@code :query}.
     *
     * <p>The full-text source is bounded to its best matches by BM25 score: a natural-language query matches most
     * documents under Lucene's default OR, and fusing all of them made hybrid search ~4x slower without changing the
     * ranking. Soft-deleted records are filtered after bounding, so only the bounded set is loaded. Candidates: as in
     * dense search on the vector side, twice the requested results on the full-text side, ten times both when a
     * metadata filter is applied afterwards.
     */
    private String buildHybridSearchQuery(
            Embedding queryEmbedding,
            String embeddingProperty,
            String textProperty,
            int maxResults,
            boolean filtered,
            boolean excludeSoftDeleted,
            String filterWhere) {
        // With filterWhere (the metadata filter in SQL) the dense source only returns allowed records, so no extra
        // candidates are needed for a filter applied afterwards (filtered). The full-text source is filtered after
        // bounding it to its best matches: filtering every match first loads all of them (about 40 ms more per query on
        // 100k NQ passages); a selective filter then leaves few lexical candidates, and the dense source still
        // provides maxResults.
        int denseCandidates = filtered ? maxResults * 10 : Math.max(maxResults * 4, maxResults + 100);
        int fullTextCandidates = filtered ? maxResults * 10 : maxResults * 2;
        String denseIndexName = typeName + "[" + embeddingProperty + "]";
        String fullTextIndexName = typeName + "[" + textProperty + "]";
        String notDeleted = String.format("(`%s` IS NULL OR `%s` != true)", PROPERTY_DELETED, PROPERTY_DELETED);
        String fullTextSource;
        if (filterWhere != null) {
            // The bounded rows carry all properties (*), so the filter can test any metadata key.
            fullTextSource = String.format(
                    "(SELECT rid, score FROM (SELECT *, @rid AS rid, $score AS score FROM `%s`"
                            + " WHERE SEARCH_INDEX('%s', :query) = true ORDER BY $score DESC LIMIT %d) WHERE %s(%s))",
                    typeName,
                    fullTextIndexName,
                    fullTextCandidates,
                    excludeSoftDeleted ? notDeleted + " AND " : "",
                    filterWhere);
        } else if (excludeSoftDeleted) {
            fullTextSource = String.format(
                    "(SELECT @rid AS rid, score FROM (SELECT @rid, `%s`, $score AS score FROM `%s`"
                            + " WHERE SEARCH_INDEX('%s', :query) = true ORDER BY $score DESC LIMIT %d) WHERE %s)",
                    PROPERTY_DELETED, typeName, fullTextIndexName, fullTextCandidates, notDeleted);
        } else {
            fullTextSource = String.format(
                    "(SELECT @rid AS rid, $score AS score FROM `%s` WHERE SEARCH_INDEX('%s', :query) = true"
                            + " ORDER BY $score DESC LIMIT %d)",
                    typeName, fullTextIndexName, fullTextCandidates);
        }
        // Only the fused rows that can be returned leave vector.fuse(): the store keeps maxResults of them (more when
        // a metadata filter is applied afterwards).
        int fusedResults = filtered ? maxResults * 10 : maxResults;
        List<String> options = new ArrayList<>();
        if (efSearch != null) {
            options.add("efSearch: " + Math.max(efSearch, denseCandidates));
        }
        if (filterWhere != null) {
            // Soft-deleted records are removed from the vector index, so the allow-list needs no deleted check (which
            // made the allow-list scan about 6x slower).
            options.add(String.format("filter: (SELECT @rid FROM `%s` WHERE %s).@rid", typeName, filterWhere));
        }
        String denseOptions = options.isEmpty() ? "" : ", { " + String.join(", ", options) + " }";
        return String.format(
                "SELECT expand(`vector.fuse`(`vector.neighbors`('%s', %s, %d%s), %s, %s))",
                denseIndexName,
                embeddingToSql(queryEmbedding),
                denseCandidates,
                denseOptions,
                fullTextSource,
                hybrid.fuseOptions(fusedResults));
    }

    private static Embedding resultToEmbedding(Object embObj) {
        if (embObj instanceof float[] vector) {
            return new Embedding(vector);
        }
        if (embObj instanceof double[] doubleVector) {
            float[] vector = new float[doubleVector.length];
            for (int j = 0; j < doubleVector.length; j++) {
                vector[j] = (float) doubleVector[j];
            }
            return new Embedding(vector);
        }
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
            if (a instanceof Float || b instanceof Float) {
                return Float.compare(((Number) a).floatValue(), ((Number) b).floatValue()) == 0;
            }
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue()) == 0;
        }
        return Objects.equals(a == null ? "" : a.toString(), b == null ? "" : b.toString());
    }

    private static int compareValues(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            if (a instanceof Float || b instanceof Float) {
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

    /** Lucene query-syntax characters, escaped with a backslash unless {@code rawFullTextQuery} is set. */
    private static final String LUCENE_SPECIAL_CHARACTERS = "\\+-!():^[]\"{}~*?|&/";

    /** Lucene's boolean operators as standalone words. */
    private static final Pattern LUCENE_OPERATOR = Pattern.compile("(?<![\\p{L}\\p{N}])(AND|OR|NOT)(?![\\p{L}\\p{N}])");

    /** The hybrid query text as passed to the full-text index (see {@link #escapeFullTextQuery}). */
    private String fullTextQuery(String text) {
        return rawFullTextQuery ? text : escapeFullTextQuery(text);
    }

    /**
     * Escapes Lucene special characters and lower-cases the operators AND, OR, NOT so they are searched as words (the
     * default analyzer lower-cases anyway). Without this, a natural-language question such as "what is 3/5?" failed to
     * parse and the hybrid search returned nothing.
     */
    static String escapeFullTextQuery(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (LUCENE_SPECIAL_CHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return LUCENE_OPERATOR
                .matcher(escaped)
                .replaceAll(match -> match.group(1).toLowerCase(Locale.ROOT));
    }

    /** The same request without the full-text query, i.e. a dense-only search. */
    private static EmbeddingSearchRequest withoutQuery(EmbeddingSearchRequest request) {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(request.queryEmbedding())
                .maxResults(request.maxResults())
                .minScore(request.minScore())
                .filter(request.filter())
                .build();
    }

    /**
     * A metadata value as a query parameter: UUIDs as strings and floats widened to doubles, as they were stored when
     * values were inlined into the SQL text.
     */
    private static Object toParameter(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        } else if (value instanceof Float f) {
            return f.doubleValue();
        }
        return value;
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
        private int batchSize = DEFAULT_REMOTE_BATCH_SIZE;
        private boolean rawFullTextQuery = false;
        private final ArcadeDBHybridSettings hybrid = new ArcadeDBHybridSettings();
        private Integer efSearch;
        private ArcadeDBVectorQuantization quantization = ArcadeDBVectorQuantization.NONE;
        private ArcadeDBFilterStrategy filterStrategy = ArcadeDBFilterStrategy.ADAPTIVE;

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
         * @param similarityFunction The similarity function of the vector index: "COSINE", "EUCLIDEAN" or
         *     "DOT_PRODUCT" (unit-length vectors only). Default: "COSINE". Scores: (1 + cosine) / 2,
         *     1 / (1 + distance), (1 + dot) / 2. Applies when the index is created.
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
         * @param beamWidth Beam width used while building the vector index graph (build time only; for the search
         *     beam see {@link #efSearch(Integer)}). Default: 100.
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

        /**
         * @param batchSize Embeddings sent per request by {@code addAll}; each request inserts them in one
         *                  transaction. Default: 500.
         */
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * @param rawFullTextQuery Whether the query text of a hybrid search is passed to the full-text index as
         *                         Lucene query syntax. Default: false — Lucene special characters and the operators
         *                         AND, OR, NOT are escaped, so natural-language queries cannot fail to parse.
         */
        public Builder rawFullTextQuery(boolean rawFullTextQuery) {
            this.rawFullTextQuery = rawFullTextQuery;
            return this;
        }

        /**
         * @param efSearch Search beam width of the vector index per query (higher: better recall, slower), at least
         *                 the number of candidates fetched. Default: {@code null} — embedded mode uses ArcadeDB's
         *                 default for its {@code max(4k, k + 100)} candidates; remote mode fetches {@code k} and uses
         *                 an equivalent beam of {@code 2 * max(4k, k + 100)}. Also applies to the dense side of
         *                 hybrid search when set.
         */
        public Builder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        /**
         * @param quantization Quantization of the vectors in the index; the stored embeddings stay full precision.
         *     Default: {@code NONE}. Applies when the index is created.
         */
        public Builder quantization(ArcadeDBVectorQuantization quantization) {
            this.quantization = quantization;
            return this;
        }

        /**
         * @param filterStrategy How searches with a metadata filter are executed. Default: {@code ADAPTIVE} (see
         *     {@link ArcadeDBFilterStrategy}).
         */
        public Builder filterStrategy(ArcadeDBFilterStrategy filterStrategy) {
            this.filterStrategy = filterStrategy;
            return this;
        }

        /**
         * @param fusion How hybrid search combines the dense and full-text rankings. Default: {@code RRF}.
         */
        public Builder hybridFusion(ArcadeDBHybridFusion fusion) {
            this.hybrid.fusion = fusion;
            return this;
        }

        /**
         * @param denseWeight    Weight of the dense (vector) ranking in hybrid search. Default: 8.
         * @param fullTextWeight Weight of the full-text (BM25) ranking in hybrid search. Default: 1.
         */
        public Builder hybridWeights(double denseWeight, double fullTextWeight) {
            this.hybrid.denseWeight = denseWeight;
            this.hybrid.fullTextWeight = fullTextWeight;
            return this;
        }

        /**
         * @param k The RRF constant {@code k} in {@code weight / (k + rank)}. Default: 60.
         */
        public Builder hybridRrfK(int k) {
            this.hybrid.rrfK = k;
            return this;
        }

        /**
         * @param analyzer Lucene analyzer class of the full-text index, e.g.
         *                 {@code org.apache.lucene.analysis.standard.StandardAnalyzer}; {@code null} for ArcadeDB's
         *                 default. Default: {@code org.apache.lucene.analysis.en.EnglishAnalyzer} (English stop words
         *                 and stemming) — choose another analyzer for text that is not English. Applies when the index
         *                 is created; an existing index keeps its analyzer.
         */
        public Builder fullTextAnalyzer(String analyzer) {
            this.hybrid.analyzer = analyzer;
            return this;
        }

        /**
         * @param k1 BM25 {@code k1} of the full-text index; {@code null} for ArcadeDB's default (1.2). Applies when the
         *           index is created.
         */
        public Builder fullTextBm25K1(Double k1) {
            this.hybrid.bm25K1 = k1;
            return this;
        }

        /**
         * @param b BM25 {@code b} of the full-text index, in [0, 1]; {@code null} for ArcadeDB's default (0.75).
         *          Applies when the index is created.
         */
        public Builder fullTextBm25B(Double b) {
            this.hybrid.bm25B = b;
            return this;
        }

        public ArcadeDBEmbeddingStore build() {
            ensureNotBlank(host, "host");
            ensureTrue(batchSize > 0, "batchSize must be positive");
            hybrid.validate();
            ensureTrue(efSearch == null || efSearch > 0, "efSearch must be positive");
            ensureNotNull(quantization, "quantization");
            ensureNotNull(filterStrategy, "filterStrategy");
            ArcadeDBVectorSimilarity.of(getOrDefault(similarityFunction, DEFAULT_SIMILARITY));
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
                    getOrDefault(metadataPrefix, REMOTE_DEFAULT_METADATA_PREFIX),
                    batchSize,
                    rawFullTextQuery,
                    hybrid.copy(),
                    efSearch,
                    quantization,
                    filterStrategy);
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
        private boolean rawFullTextQuery = false;
        private int batchSize = DEFAULT_EMBEDDED_BATCH_SIZE;
        private final ArcadeDBHybridSettings hybrid = new ArcadeDBHybridSettings();
        private Integer efSearch;
        private String similarityFunction = DEFAULT_SIMILARITY;
        private ArcadeDBVectorQuantization quantization = ArcadeDBVectorQuantization.NONE;
        private ArcadeDBFilterStrategy filterStrategy = ArcadeDBFilterStrategy.ADAPTIVE;
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
         * @param beamWidth Beam width used while building the vector index graph (build time only; for the search
         *     beam see {@link #efSearch(Integer)}). Default: 100.
         */
        public EmbeddedBuilder beamWidth(int beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        /**
         * @param rawFullTextQuery Whether the query text of a hybrid search is passed to the full-text index as
         *                         Lucene query syntax. Default: false — Lucene special characters and the operators
         *                         AND, OR, NOT are escaped, so natural-language queries cannot fail to parse.
         */
        public EmbeddedBuilder rawFullTextQuery(boolean rawFullTextQuery) {
            this.rawFullTextQuery = rawFullTextQuery;
            return this;
        }

        /**
         * @param efSearch Search beam width of the vector index per query (higher: better recall, slower), at least
         *                 the number of candidates fetched. Default: {@code null} — embedded mode uses ArcadeDB's
         *                 default for its {@code max(4k, k + 100)} candidates; remote mode fetches {@code k} and uses
         *                 an equivalent beam of {@code 2 * max(4k, k + 100)}. Also applies to the dense side of
         *                 hybrid search when set.
         */
        public EmbeddedBuilder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        /**
         * @param quantization Quantization of the vectors in the index; the stored embeddings stay full precision.
         *     Default: {@code NONE}. Applies when the index is created.
         */
        public EmbeddedBuilder quantization(ArcadeDBVectorQuantization quantization) {
            this.quantization = quantization;
            return this;
        }

        /**
         * @param filterStrategy How searches with a metadata filter are executed. Default: {@code ADAPTIVE} (see
         *     {@link ArcadeDBFilterStrategy}).
         */
        public EmbeddedBuilder filterStrategy(ArcadeDBFilterStrategy filterStrategy) {
            this.filterStrategy = filterStrategy;
            return this;
        }

        /**
         * @param similarityFunction The similarity function of the vector index: "COSINE", "EUCLIDEAN" or
         *     "DOT_PRODUCT" (unit-length vectors only). Default: "COSINE". Scores: (1 + cosine) / 2,
         *     1 / (1 + distance), (1 + dot) / 2. Applies when the index is created.
         */
        public EmbeddedBuilder similarityFunction(String similarityFunction) {
            this.similarityFunction = similarityFunction;
            return this;
        }

        /**
         * @param fusion How hybrid search combines the dense and full-text rankings. Default: {@code RRF}.
         */
        public EmbeddedBuilder hybridFusion(ArcadeDBHybridFusion fusion) {
            this.hybrid.fusion = fusion;
            return this;
        }

        /**
         * @param denseWeight    Weight of the dense (vector) ranking in hybrid search. Default: 8.
         * @param fullTextWeight Weight of the full-text (BM25) ranking in hybrid search. Default: 1.
         */
        public EmbeddedBuilder hybridWeights(double denseWeight, double fullTextWeight) {
            this.hybrid.denseWeight = denseWeight;
            this.hybrid.fullTextWeight = fullTextWeight;
            return this;
        }

        /**
         * @param k The RRF constant {@code k} in {@code weight / (k + rank)}. Default: 60.
         */
        public EmbeddedBuilder hybridRrfK(int k) {
            this.hybrid.rrfK = k;
            return this;
        }

        /**
         * @param analyzer Lucene analyzer class of the full-text index, e.g.
         *                 {@code org.apache.lucene.analysis.standard.StandardAnalyzer}; {@code null} for ArcadeDB's
         *                 default. Default: {@code org.apache.lucene.analysis.en.EnglishAnalyzer} (English stop words
         *                 and stemming) — choose another analyzer for text that is not English. Applies when the index
         *                 is created; an existing index keeps its analyzer.
         */
        public EmbeddedBuilder fullTextAnalyzer(String analyzer) {
            this.hybrid.analyzer = analyzer;
            return this;
        }

        /**
         * @param k1 BM25 {@code k1} of the full-text index; {@code null} for ArcadeDB's default (1.2). Applies when the
         *           index is created.
         */
        public EmbeddedBuilder fullTextBm25K1(Double k1) {
            this.hybrid.bm25K1 = k1;
            return this;
        }

        /**
         * @param b BM25 {@code b} of the full-text index, in [0, 1]; {@code null} for ArcadeDB's default (0.75).
         *          Applies when the index is created.
         */
        public EmbeddedBuilder fullTextBm25B(Double b) {
            this.hybrid.bm25B = b;
            return this;
        }

        /**
         * @param batchSize Embeddings inserted per transaction by {@code addAll}. Default: 1000.
         */
        public EmbeddedBuilder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public ArcadeDBEmbeddingStore build() {
            ensureTrue(dimension > 0, "dimension must be positive");
            ensureTrue(batchSize > 0, "batchSize must be positive");
            hybrid.validate();
            ensureTrue(efSearch == null || efSearch > 0, "efSearch must be positive");
            ensureNotNull(quantization, "quantization");
            ensureNotNull(filterStrategy, "filterStrategy");
            ArcadeDBVectorSimilarity.of(getOrDefault(similarityFunction, DEFAULT_SIMILARITY));
            return new ArcadeDBEmbeddingStore(this);
        }
    }
}
