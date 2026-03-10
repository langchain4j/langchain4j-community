package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import com.arcadedb.remote.RemoteDatabase;
import com.arcadedb.remote.RemoteServer;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link EmbeddingStore} using <a href="https://arcadedb.com/">ArcadeDB</a>
 * with LSM_VECTOR (JVector-based HNSW) vector indexing.
 * <p>
 * Connects to a remote ArcadeDB server and stores embeddings as vertex documents
 * with an LSM_VECTOR index for efficient similarity search.
 */
public class ArcadeDBEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ArcadeDBEmbeddingStore.class);

    private static final String DEFAULT_TYPE_NAME = "EmbeddingDocument";
    private static final String DEFAULT_SIMILARITY = "COSINE";
    private static final String DEFAULT_METADATA_PREFIX = "meta_";
    private static final int DEFAULT_PORT = 2480;
    private static final int DEFAULT_MAX_CONNECTIONS = 16;
    private static final int DEFAULT_BEAM_WIDTH = 100;

    static final String ID_PROPERTY = "doc_id";
    static final String EMBEDDING_PROPERTY = "embedding";
    static final String TEXT_PROPERTY = "text";

    private final RemoteDatabase database;
    private final String typeName;
    private final String metadataPrefix;
    private final ArcadeDBMetadataFilterMapper filterMapper;

    private ArcadeDBEmbeddingStore(
            RemoteDatabase database,
            String typeName,
            int dimension,
            String similarityFunction,
            int maxConnections,
            int beamWidth,
            String metadataPrefix) {
        this.database = ensureNotNull(database, "database");
        this.typeName = ensureNotBlank(typeName, "typeName");
        this.metadataPrefix = metadataPrefix;
        this.filterMapper = new ArcadeDBMetadataFilterMapper(metadataPrefix);
        initSchema(dimension, similarityFunction, maxConnections, beamWidth);
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

        log.debug("Inserting {} embeddings into ArcadeDB", ids.size());
        for (int i = 0; i < ids.size(); i++) {
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO `").append(typeName).append("` SET ");
            sql.append(ID_PROPERTY)
                    .append(" = '")
                    .append(escapeString(ids.get(i)))
                    .append("'");
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
                Map<String, Object> metadataMap = segment.metadata().toMap();
                for (Map.Entry<String, Object> entry : metadataMap.entrySet()) {
                    sql.append(", ")
                            .append(metadataPrefix)
                            .append(entry.getKey())
                            .append(" = ")
                            .append(valueToSql(entry.getValue()));
                }
            }

            database.command("sql", sql.toString());
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        String idList = ids.stream().map(id -> "'" + escapeString(id) + "'").collect(Collectors.joining(", "));
        String sql = String.format("DELETE FROM `%s` WHERE %s IN [%s]", typeName, ID_PROPERTY, idList);
        database.command("sql", sql);
        rebuildIndex();
    }

    @Override
    public void removeAll(Filter filter) {

        ensureNotNull(filter, "filter");
        // Print the count of all embeddings before the deletion
        log.info(String.format(
                "Number of embeddings before delete: %d", findAll(1000).size()));
        String whereClause = filterMapper.map(filter);
        String sql = String.format("DELETE FROM `%s` WHERE %s", typeName, whereClause);
        log.debug("Removing with filter: {}", sql);
        database.command("sql", sql);
        log.info(String.format(
                "Number of embeddings after delete: %d", findAll(1000).size()));
        rebuildIndex();
    }

    @Override
    public void removeAll() {
        database.command("sql", String.format("DELETE FROM `%s`", typeName));
        rebuildIndex();
    }

    private void rebuildIndex() {
        // ArcadeDB's LSM_VECTOR index requires a rebuild after deletions to ensure
        // that subsequent inserts are properly indexed for vector search.
        database.command("sql", "REBUILD INDEX *");
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding queryEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        // Validate the filter eagerly so unsupported filter types (e.g. containsString)
        // throw UnsupportedOperationException even when the index is empty.
        if (filter != null) {
            validateFilter(filter);
        }

        // Fetch extra candidates to account for in-memory filtering reducing the result set.
        int fetchCount = filter != null ? maxResults * 5 : maxResults;

        String vectorSql = embeddingToSql(queryEmbedding);
        String indexName = typeName + "[" + EMBEDDING_PROPERTY + "]";
        // Run the neighbor search without any WHERE clause; filtering is done in-memory below.
        String query = String.format(
                "SELECT *, `vector.neighbors`('%s', %s, %d) AS neighbors FROM `%s`",
                indexName, vectorSql, fetchCount, typeName);

        ResultSet resultSet;
        try {
            resultSet = database.query("sql", query);
        } catch (Exception e) {
            log.debug("Vector search returned error (index may be empty): {}", e.getMessage());
            return new EmbeddingSearchResult<>(List.of());
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        while (resultSet.hasNext() && matches.size() < maxResults) {
            Result doc = resultSet.next();

            String docId = doc.getProperty(ID_PROPERTY);
            if (docId == null) {
                continue;
            }
            Embedding embedding = toEmbedding(doc.getProperty(EMBEDDING_PROPERTY));
            if (embedding == null) {
                continue;
            }

            double score = RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(queryEmbedding, embedding));
            if (score < minScore) {
                continue;
            }

            String text = doc.hasProperty(TEXT_PROPERTY) ? doc.getProperty(TEXT_PROPERTY) : null;
            Map<String, Object> metadataMap = extractMetadataFromResult(doc);

            // Apply filter in-memory against the document's metadata.
            if (filter != null && !matchesFilter(filter, metadataMap)) {
                continue;
            }

            TextSegment textSegment = null;
            if (text != null) {
                textSegment = TextSegment.from(text, Metadata.from(metadataMap));
            } else if (!metadataMap.isEmpty()) {
                textSegment = TextSegment.from("", Metadata.from(metadataMap));
            }

            matches.add(new EmbeddingMatch<>(score, docId, embedding, textSegment));
        }

        matches.sort((a, b) -> Double.compare(b.score(), a.score()));

        return new EmbeddingSearchResult<>(matches);
    }

    /**
     * Validates that the filter only contains supported types, throwing
     * {@link UnsupportedOperationException} eagerly for unsupported filter types.
     */
    private void validateFilter(Filter filter) {
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
                || filter instanceof IsNotIn)) {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    /**
     * Evaluates a {@link Filter} in-memory against a document's metadata map.
     * Metadata keys in the map are already stripped of their prefix.
     */
    private boolean matchesFilter(Filter filter, Map<String, Object> metadata) {
        if (filter instanceof IsEqualTo f) {
            Object value = metadata.get(f.key());
            return value != null && compareValues(value, f.comparisonValue()) == 0;
        } else if (filter instanceof IsNotEqualTo f) {
            Object value = metadata.get(f.key());
            return value == null || compareValues(value, f.comparisonValue()) != 0;
        } else if (filter instanceof IsGreaterThan f) {
            Object value = metadata.get(f.key());
            return value != null && compareValues(value, f.comparisonValue()) > 0;
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            Object value = metadata.get(f.key());
            return value != null && compareValues(value, f.comparisonValue()) >= 0;
        } else if (filter instanceof IsLessThan f) {
            Object value = metadata.get(f.key());
            return value != null && compareValues(value, f.comparisonValue()) < 0;
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            Object value = metadata.get(f.key());
            return value != null && compareValues(value, f.comparisonValue()) <= 0;
        } else if (filter instanceof IsIn f) {
            Object value = metadata.get(f.key());
            if (value == null) return false;
            for (Object candidate : f.comparisonValues()) {
                if (compareValues(value, candidate) == 0) return true;
            }
            return false;
        } else if (filter instanceof IsNotIn f) {
            Object value = metadata.get(f.key());
            if (value == null) return true;
            for (Object candidate : f.comparisonValues()) {
                if (compareValues(value, candidate) == 0) return false;
            }
            return true;
        } else if (filter instanceof And f) {
            return matchesFilter(f.left(), metadata) && matchesFilter(f.right(), metadata);
        } else if (filter instanceof Or f) {
            return matchesFilter(f.left(), metadata) || matchesFilter(f.right(), metadata);
        } else if (filter instanceof Not f) {
            return !matchesFilter(f.expression(), metadata);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    /**
     * Compares two values for ordering. Numbers are compared numerically; when the expected
     * value is a {@link Float} the comparison is done at float precision so that a value
     * stored as {@code 1.1f} and returned by ArcadeDB as the JSON literal {@code 1.1}
     * (parsed by Java as {@code Double(1.1)}) still matches the original {@code Float(1.1f)}.
     * Everything else falls back to string comparison.
     */
    private int compareValues(Object actual, Object expected) {
        if (actual instanceof Number && expected instanceof Number) {
            if (expected instanceof Float) {
                return Float.compare(((Number) actual).floatValue(), ((Number) expected).floatValue());
            }
            return Double.compare(((Number) actual).doubleValue(), ((Number) expected).doubleValue());
        }
        return actual.toString().compareTo(expected.toString());
    }

    private Embedding toEmbedding(Object embObj) {
        if (embObj instanceof List<?> embList) {
            float[] vector = new float[embList.size()];
            for (int j = 0; j < embList.size(); j++) {
                vector[j] = ((Number) embList.get(j)).floatValue();
            }
            return new Embedding(vector);
        }
        return null;
    }

    private Map<String, Object> extractMetadataFromResult(Result doc) {
        Map<String, Object> metadata = new HashMap<>();
        for (String prop : doc.getPropertyNames()) {
            if (prop.startsWith(metadataPrefix)) {
                String key = prop.substring(metadataPrefix.length());
                metadata.put(key, doc.getProperty(prop));
            }
        }
        return metadata;
    }

    private void initSchema(int dimension, String similarityFunction, int maxConnections, int beamWidth) {
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
                typeName,
                ID_PROPERTY,
                typeName,
                EMBEDDING_PROPERTY,
                typeName,
                TEXT_PROPERTY,
                typeName,
                EMBEDDING_PROPERTY,
                dimension,
                similarityFunction,
                maxConnections,
                beamWidth);

        log.debug("Initializing ArcadeDB schema for embedding store");
        database.command("sqlscript", script);
    }

    private static String embeddingToSql(Embedding embedding) {
        float[] vector = embedding.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String valueToSql(Object value) {
        if (value instanceof String || value instanceof java.util.UUID) {
            return "'" + escapeString(value.toString()) + "'";
        } else if (value instanceof Long l) {
            // ArcadeDB's SQL parser can't handle Long.MIN_VALUE directly because
            // -(9223372036854775808) overflows. Express it as (MIN+1)-1 workaround.
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

    /**
     * Retrieves all stored embeddings using a SQL scan (no vector index).
     * This is useful when an exhaustive listing is needed, since the HNSW
     * approximate index may not return every document for large result sets
     * with many identical vectors.
     */
    List<EmbeddingMatch<TextSegment>> findAll(int maxResults) {
        String sql = String.format("SELECT * FROM `%s` LIMIT %d", typeName, maxResults);
        ResultSet resultSet = database.query("sql", sql);

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        while (resultSet.hasNext()) {
            Result doc = resultSet.next();
            String docId = doc.getProperty(ID_PROPERTY);
            if (docId == null) {
                continue;
            }
            Embedding embedding = toEmbedding(doc.getProperty(EMBEDDING_PROPERTY));
            if (embedding == null) {
                continue;
            }
            String text = doc.hasProperty(TEXT_PROPERTY) ? doc.getProperty(TEXT_PROPERTY) : null;
            Map<String, Object> metadataMap = extractMetadataFromResult(doc);

            TextSegment textSegment = null;
            if (text != null) {
                textSegment = TextSegment.from(text, Metadata.from(metadataMap));
            } else if (!metadataMap.isEmpty()) {
                textSegment = TextSegment.from("", Metadata.from(metadataMap));
            }

            matches.add(new EmbeddingMatch<>(1.0, docId, embedding, textSegment));
        }
        log.info("findAll: SQL returned {} matches", matches.size());
        return matches;
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
    }

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
        private String metadataPrefix = DEFAULT_METADATA_PREFIX;

        /**
         * @param host ArcadeDB server hostname. Required.
         * @return builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port ArcadeDB server HTTP port. Default: 2480.
         * @return builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * @param databaseName Name of the ArcadeDB database. Required.
         * @return builder
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * @param username ArcadeDB server username. Required.
         * @return builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * @param password ArcadeDB server password. Required.
         * @return builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param typeName The vertex type name for storing embeddings. Default: "EmbeddingDocument".
         * @return builder
         */
        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        /**
         * @param dimension The dimensionality of the embedding vectors. Required.
         * @return builder
         */
        public Builder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param similarityFunction The similarity function for vector comparison. Default: "COSINE".
         *                           Options: "COSINE", "EUCLIDEAN", "SQUARED_EUCLIDEAN".
         * @return builder
         */
        public Builder similarityFunction(String similarityFunction) {
            this.similarityFunction = similarityFunction;
            return this;
        }

        /**
         * @param maxConnections Maximum number of connections per node in the HNSW graph. Default: 16.
         * @return builder
         */
        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * @param beamWidth Beam width for HNSW index search. Default: 100.
         * @return builder
         */
        public Builder beamWidth(int beamWidth) {
            this.beamWidth = beamWidth;
            return this;
        }

        /**
         * @param createDatabase Whether to create the database if it does not exist. Default: false.
         * @return builder
         */
        public Builder createDatabase(boolean createDatabase) {
            this.createDatabase = createDatabase;
            return this;
        }

        /**
         * @param metadataPrefix Prefix for metadata properties stored on the vertex. Default: "meta_".
         * @return builder
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
                    getOrDefault(metadataPrefix, DEFAULT_METADATA_PREFIX));
        }
    }
}
