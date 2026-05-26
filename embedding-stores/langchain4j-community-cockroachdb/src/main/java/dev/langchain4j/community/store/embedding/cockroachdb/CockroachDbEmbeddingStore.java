package dev.langchain4j.community.store.embedding.cockroachdb;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.store.embedding.cockroachdb.index.BaseIndex;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CockroachDB-backed {@link EmbeddingStore} for {@link TextSegment}s.
 *
 * <p>Mirrors the feature set of the Python {@code langchain-cockroachdb} library:
 * native {@code VECTOR} column, JSONB metadata + filtering, optional namespace
 * column for multi-tenancy, and (when paired with
 * {@link dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex})
 * per-query {@code vector_search_beam_size} tuning.
 *
 * <pre>{@code
 * CockroachDbEngine engine = CockroachDbEngine.builder()
 *         .host("localhost").port(26257).database("defaultdb")
 *         .username("root").build();
 *
 * CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
 *         .engine(engine)
 *         .schema(CockroachDbSchema.builder()
 *                 .dimension(384)
 *                 .vectorIndex(CSpannIndex.builder().build())
 *                 .build())
 *         .build();
 * }</pre>
 */
public class CockroachDbEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger logger = LoggerFactory.getLogger(CockroachDbEmbeddingStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CockroachDbEngine engine;
    private final CockroachDbSchema schema;
    private final CockroachDbFilterMapper filterMapper;
    private final String namespace; // null = no scoping even when schema has namespace column
    private final Integer searchBeamSize;

    public CockroachDbEmbeddingStore(Builder builder) {
        this.engine = builder.engine;
        this.schema = builder.schema;
        this.filterMapper = new CockroachDbFilterMapper(schema.getMetadataColumn());
        this.namespace = builder.namespace;
        this.searchBeamSize = builder.searchBeamSize;

        if (schema.isCreateTableIfNotExists()) {
            createTableIfNotExists();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Serialize the embedding to CockroachDB's text form: {@code "[v1,v2,...]"}.
     * <p>CockroachDB's pgwire layer does not accept the binary format for the VECTOR
     * type, so all vectors are sent as text and cast to {@code ?::vector} in SQL.
     */
    private static String toVectorLiteral(Embedding embedding) {
        List<Float> list = embedding.vectorAsList();
        StringBuilder sb = new StringBuilder(list.size() * 12).append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(list.get(i).floatValue());
        }
        return sb.append(']').toString();
    }

    private static Embedding extractEmbedding(ResultSet rs, String column) throws SQLException {
        Object o = rs.getObject(column);
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        String[] parts = s.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Float.parseFloat(parts[i].trim());
        return Embedding.from(arr);
    }

    private static String toJson(Metadata metadata) {
        Map<String, Object> map = metadata == null ? new HashMap<>() : new HashMap<>(metadata.toMap());
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new CockroachDbRequestFailedException("Failed to serialize metadata", e);
        }
    }

    private static Metadata readMetadata(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isEmpty()) return new Metadata();
        try {
            Map<String, Object> map = JSON.readValue(raw, Map.class);
            return Metadata.from(map);
        } catch (JsonProcessingException e) {
            throw new CockroachDbRequestFailedException("Failed to deserialize metadata: " + raw, e);
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
        List<String> ids = embeddings.stream().map(e -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException("embeddings and textSegments must have the same size");
        }
        List<String> ids = embeddings.stream().map(e -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, textSegments);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (ids.size() != embeddings.size() || (textSegments != null && embeddings.size() != textSegments.size())) {
            throw new IllegalArgumentException("ids, embeddings and textSegments must have the same size");
        }
        addAllInternal(ids, embeddings, textSegments);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding query = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        String op = schema.getMetricType().operator();
        StringBuilder sql = new StringBuilder()
                .append("SELECT ")
                .append(schema.getIdColumn())
                .append(", ")
                .append(schema.getContentColumn())
                .append(", ")
                .append(schema.getMetadataColumn())
                .append(", ")
                .append(schema.getEmbeddingColumn())
                .append(", ")
                .append(schema.getEmbeddingColumn())
                .append(" ")
                .append(op)
                .append(" ?::vector AS distance ")
                .append("FROM ")
                .append(schema.getFullTableName());

        List<String> wheres = new ArrayList<>();
        if (namespaceClause() != null) wheres.add(namespaceClause());
        if (filter != null) wheres.add(filterMapper.map(filter));
        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", wheres));
        }
        // Operator + bound vector must be repeated for the C-SPANN index to fire.
        sql.append(" ORDER BY ")
                .append(schema.getEmbeddingColumn())
                .append(" ")
                .append(op)
                .append(" ?::vector LIMIT ?");

        String queryVector = toVectorLiteral(query);
        String sqlString = sql.toString();

        return RetryUtils.withRetry(() -> {
            try (Connection conn = engine.getConnection()) {
                if (searchBeamSize != null) {
                    // SET LOCAL is transaction-scoped; needs an explicit txn so the setting doesn't
                    // leak back into the pool on connection return.
                    conn.setAutoCommit(false);
                }
                try (PreparedStatement st = conn.prepareStatement(sqlString)) {
                    if (searchBeamSize != null) {
                        try (Statement s = conn.createStatement()) {
                            s.execute("SET LOCAL vector_search_beam_size = " + searchBeamSize);
                        }
                    }
                    st.setString(1, queryVector);
                    st.setString(2, queryVector);
                    st.setInt(3, maxResults);

                    try (ResultSet rs = st.executeQuery()) {
                        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
                        while (rs.next()) {
                            String id = rs.getString(schema.getIdColumn());
                            String content = rs.getString(schema.getContentColumn());
                            double distance = rs.getDouble("distance");
                            double score = scoreFromDistance(distance);
                            if (score < minScore) continue;
                            Embedding emb = extractEmbedding(rs, schema.getEmbeddingColumn());
                            Metadata metadata = readMetadata(rs, schema.getMetadataColumn());
                            TextSegment seg = isNotNullOrBlank(content) ? TextSegment.from(content, metadata) : null;
                            matches.add(new EmbeddingMatch<>(score, id, emb, seg));
                        }
                        if (searchBeamSize != null) conn.commit();
                        return new EmbeddingSearchResult<>(matches);
                    }
                } catch (SQLException e) {
                    if (searchBeamSize != null) {
                        try {
                            conn.rollback();
                        } catch (SQLException ignore) {
                            /* best-effort */
                        }
                    }
                    throw e;
                }
            }
        });
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }
        String sql = String.format(
                "DELETE FROM %s WHERE %s = ?%s",
                schema.getFullTableName(),
                schema.getIdColumn(),
                namespaceClause() == null ? "" : " AND " + namespaceClause());

        RetryUtils.withRetry(() -> {
            try (Connection conn = engine.getConnection();
                    PreparedStatement st = conn.prepareStatement(sql)) {
                for (String id : ids) {
                    st.setObject(1, UUID.fromString(id));
                    st.addBatch();
                }
                st.executeBatch();
                return null;
            }
        });
    }

    @Override
    public void removeAll(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter cannot be null");
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(schema.getFullTableName())
                .append(" WHERE ")
                .append(filterMapper.map(filter));
        if (namespaceClause() != null) sql.append(" AND ").append(namespaceClause());

        RetryUtils.withRetry(() -> {
            try (Connection conn = engine.getConnection();
                    Statement st = conn.createStatement()) {
                st.executeUpdate(sql.toString());
                return null;
            }
        });
    }

    @Override
    public void removeAll() {
        String sql;
        if (namespaceClause() != null) {
            sql = "DELETE FROM " + schema.getFullTableName() + " WHERE " + namespaceClause();
        } else {
            sql = "DELETE FROM " + schema.getFullTableName();
        }
        RetryUtils.withRetry(() -> {
            try (Connection conn = engine.getConnection();
                    Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
                return null;
            }
        });
    }

    public void createTableIfNotExists() {
        RetryUtils.withRetry(() -> {
            try (Connection conn = engine.getConnection();
                    Statement st = conn.createStatement()) {
                st.execute(schema.getCreateTableSql());

                String nsIdx = schema.getCreateNamespaceIndexSql();
                if (nsIdx != null) st.execute(nsIdx);

                String tsAlter = schema.getAddTsvectorColumnSql();
                if (tsAlter != null) {
                    st.execute(tsAlter);
                    st.execute(schema.getCreateTsvectorIndexSql());
                }

                String vecIdx = schema.getCreateVectorIndexSql();
                if (vecIdx != null) {
                    logger.info("Creating vector index: {}", vecIdx);
                    st.execute(vecIdx);
                }
                return null;
            }
        });
    }

    private void addInternal(String id, Embedding embedding, TextSegment segment) {
        addAllInternal(singletonList(id), singletonList(embedding), segment != null ? singletonList(segment) : null);
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        if (ids.isEmpty()) return;

        StringBuilder columns = new StringBuilder().append(schema.getIdColumn()).append(", ");
        if (schema.hasNamespace()) columns.append(schema.getNamespaceColumn()).append(", ");
        columns.append(schema.getContentColumn())
                .append(", ")
                .append(schema.getEmbeddingColumn())
                .append(", ")
                .append(schema.getMetadataColumn());

        StringBuilder placeholders = new StringBuilder("?");
        if (schema.hasNamespace()) placeholders.append(", ?");
        placeholders.append(", ?, ?::vector, ?::jsonb");

        StringBuilder updateSet = new StringBuilder()
                .append(schema.getContentColumn())
                .append(" = EXCLUDED.")
                .append(schema.getContentColumn())
                .append(", ")
                .append(schema.getEmbeddingColumn())
                .append(" = EXCLUDED.")
                .append(schema.getEmbeddingColumn())
                .append(", ")
                .append(schema.getMetadataColumn())
                .append(" = EXCLUDED.")
                .append(schema.getMetadataColumn());
        if (schema.hasNamespace()) {
            updateSet
                    .append(", ")
                    .append(schema.getNamespaceColumn())
                    .append(" = EXCLUDED.")
                    .append(schema.getNamespaceColumn());
        }

        String sql = String.format(
                "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
                schema.getFullTableName(), columns, placeholders, schema.getIdColumn(), updateSet);

        String resolvedNamespace = namespace != null ? namespace : "";

        RetryUtils.withRetry(() -> {
            try (Connection conn = engine.getConnection();
                    PreparedStatement st = conn.prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) {
                    TextSegment seg = segments != null ? segments.get(i) : null;
                    Metadata metadata = seg != null ? seg.metadata() : new Metadata();
                    String content = seg != null ? seg.text() : null;

                    int p = 1;
                    st.setObject(p++, UUID.fromString(ids.get(i)));
                    if (schema.hasNamespace()) st.setString(p++, resolvedNamespace);
                    st.setString(p++, content);
                    st.setString(p++, toVectorLiteral(embeddings.get(i)));
                    st.setString(p, toJson(metadata));
                    st.addBatch();
                }
                st.executeBatch();
                return null;
            }
        });
    }

    private String namespaceClause() {
        if (namespace == null || !schema.hasNamespace()) return null;
        return schema.getNamespaceColumn() + " = '" + namespace.replace("'", "''") + "'";
    }

    private double scoreFromDistance(double distance) {
        switch (schema.getMetricType()) {
            case COSINE:
                return RelevanceScore.fromCosineSimilarity(1.0 - distance);
            case EUCLIDEAN:
                return RelevanceScore.fromCosineSimilarity(1.0 / (1.0 + distance));
            case DOT_PRODUCT:
                // pgvector / CRDB return negative inner product
                return Math.abs(distance) + 1.0;
            default:
                return RelevanceScore.fromCosineSimilarity(1.0 - distance);
        }
    }

    public static class Builder {
        private CockroachDbEngine engine;
        private CockroachDbSchema schema;
        private String namespace;
        private Integer searchBeamSize;
        private CockroachDbSchema.Builder schemaBuilder;

        public Builder engine(CockroachDbEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder schema(CockroachDbSchema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Tenant value to set on writes and filter on reads. Requires that the
         * underlying {@link CockroachDbSchema} has a {@code namespaceColumn} configured.
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Override CockroachDB's per-query {@code vector_search_beam_size} session
         * variable. Only meaningful when the vector index is C-SPANN. When set,
         * each search runs inside an explicit transaction to scope the {@code SET LOCAL}.
         */
        public Builder searchBeamSize(Integer searchBeamSize) {
            this.searchBeamSize = searchBeamSize;
            return this;
        }

        public Builder dimension(Integer dimension) {
            ensureSchemaBuilder().dimension(dimension);
            return this;
        }

        public Builder tableName(String tableName) {
            ensureSchemaBuilder().tableName(tableName);
            return this;
        }

        public Builder schemaName(String schemaName) {
            ensureSchemaBuilder().schemaName(schemaName);
            return this;
        }

        public Builder metricType(MetricType metricType) {
            ensureSchemaBuilder().metricType(metricType);
            return this;
        }

        public Builder vectorIndex(BaseIndex vectorIndex) {
            ensureSchemaBuilder().vectorIndex(vectorIndex);
            return this;
        }

        public Builder namespaceColumn(String namespaceColumn) {
            ensureSchemaBuilder().namespaceColumn(namespaceColumn);
            return this;
        }

        public Builder createTsvectorColumn(boolean enable) {
            ensureSchemaBuilder().createTsvectorColumn(enable);
            return this;
        }

        public Builder createTableIfNotExists(boolean create) {
            ensureSchemaBuilder().createTableIfNotExists(create);
            return this;
        }

        private CockroachDbSchema.Builder ensureSchemaBuilder() {
            if (schemaBuilder == null) schemaBuilder = CockroachDbSchema.builder();
            return schemaBuilder;
        }

        public CockroachDbEmbeddingStore build() {
            if (engine == null) {
                throw new IllegalArgumentException("CockroachDbEngine is required");
            }
            if (schema == null) {
                if (schemaBuilder == null) {
                    throw new IllegalArgumentException("Either schema or dimension must be specified");
                }
                schema = schemaBuilder.build();
            }
            if (namespace != null && !schema.hasNamespace()) {
                throw new IllegalArgumentException(
                        "namespace was set on the store but the schema has no namespaceColumn configured");
            }
            return new CockroachDbEmbeddingStore(this);
        }
    }
}
