package dev.langchain4j.community.store.embedding.duckdb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of  {@link EmbeddingStore} using <a href="https://duckdb.org/">DuckDB</a>
 * This implementation uses cosine distance and supports storing {@link Metadata}
 */
public class DuckDBEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(DuckDBEmbeddingStore.class);

    private static final String CREATE_TABLE_TEMPLATE =
            """
            create table if not exists %s (id UUID, embedding FLOAT[], text TEXT NULL, metadata JSON NULL);
            """;

    private static final String SEARCH_QUERY_TEMPLATE =
            """
            select id, embedding, text, metadata, (list_cosine_similarity(embedding,%s)+1.0)/2.0 as score
            from %s
            where score >= %s %s
            order by score DESC
            limit %d
            """;

    private static final String INSERT_QUERY_TEMPLATE =
            """
            insert into %s (id, embedding, text, metadata) values (?,?,?,?)
            """;

    private static final String DELETE_BY_IDS_QUERY_TEMPLATE = """
        delete from %s where id in ?
        """;

    private static final String DELETE_QUERY_TEMPLATE = """
            delete from %s where %s
            """;

    private static final String TRUNCATE_QUERY_TEMPLATE = """
            truncate table %s
            """;

    private final String tableName;
    private final DuckDBConnection duckDBConnection;
    private final DuckDBMetadataFilterMapper jsonFilterMapper = new DuckDBMetadataFilterMapper();
    private final ObjectMapper jsonMetadataSerializer = new ObjectMapper();

    /**
     * Initializes a new instance of DuckDBEmbeddingStore with the specified parameters.
     *
     * @param filePath  File used to persist DuckDB database. If not specified, the database will be stored in-memory.
     * @param tableName The database table name to use. If not specified, "embeddings" will be used
     */
    public DuckDBEmbeddingStore(String filePath, String tableName) {
        try {
            var dbUrl = filePath != null ? "jdbc:duckdb:" + filePath : "jdbc:duckdb:";
            this.tableName = getOrDefault(tableName, "embeddings");
            this.duckDBConnection = (DuckDBConnection) DriverManager.getConnection(dbUrl);
            initTable();
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to load duckdb connection", e);
        }
    }

    /**
     * @return a new instance of DuckDBEmbeddingStore with the default configuration and database stored in-memory
     */
    public static DuckDBEmbeddingStore inMemory() {
        return new DuckDBEmbeddingStore(null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String filePath;
        private String tableName;

        /**
         * @param filePath File used to persist DuckDB database. If not specified, the database will be stored in-memory.
         * @return builder
         */
        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * @param tableName The database table name to use. If not specified, "embeddings" will be used
         * @return builder
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * @param tableName The database table name to use. If not specified, "embeddings" will be used
         * @return builder
         */
        public Builder inMemory(String tableName) {
            return filePath(null);
        }

        public DuckDBEmbeddingStore build() {
            return new DuckDBEmbeddingStore(filePath, tableName);
        }
    }

    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
        addAll(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        String sql = format(DELETE_BY_IDS_QUERY_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(sql)) {
            var idsParam = connection.createArrayOf("UUID", ids.toArray());
            statement.setObject(1, idsParam);
            statement.execute();
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to remove embeddings by ids", e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        var whereClause = jsonFilterMapper.map(filter);
        String sql = format(DELETE_QUERY_TEMPLATE, tableName, whereClause);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(sql)) {
            log.debug(sql);
            statement.execute();
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to remove embeddings with filter", e);
        }
    }

    @Override
    public void removeAll() {
        var sql = format(TRUNCATE_QUERY_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException("Unable to remove all embeddings", e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        var param = embeddingToParam(request.queryEmbedding());
        var filterClause = request.filter() != null ? "and " + jsonFilterMapper.map(request.filter()) : "";
        var query =
                format(SEARCH_QUERY_TEMPLATE, param, tableName, request.minScore(), filterClause, request.maxResults());

        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(query)) {
            var matches = new ArrayList<EmbeddingMatch<TextSegment>>();

            log.debug(query);
            var resultSet = statement.executeQuery();
            while (resultSet.next()) {

                var id = resultSet.getString("id");
                var text = resultSet.getString("text");
                var score = resultSet.getDouble("score");
                var sqlArray = resultSet.getArray("embedding");
                var metadataJson = resultSet.getString("metadata");

                var typeReference = new TypeReference<HashMap<String, Object>>() {};
                Map<String, ?> metadataMap = metadataJson != null
                        ? jsonMetadataSerializer.readValue(metadataJson, typeReference)
                        : Collections.emptyMap();

                var sqlList = (Object[]) sqlArray.getArray();
                var vector = new float[sqlList.length];
                for (int i = 0; i < sqlList.length; i++) {
                    vector[i] = (float) sqlList[i];
                }
                var ts = text != null ? TextSegment.from(text, Metadata.from(metadataMap)) : null;
                matches.add(new EmbeddingMatch<>(score, id, new Embedding(vector), ts));
            }
            return new EmbeddingSearchResult<>(matches);
        } catch (SQLException | JsonProcessingException e) {
            throw new DuckDBSQLException("Error while searching embeddings", e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), embedding == null ? null : singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[no embeddings to add to DuckDB]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(format(INSERT_QUERY_TEMPLATE, tableName))) {
            for (int i = 0; i < ids.size(); i++) {
                String textParam = null;
                if (embedded != null && embedded.get(i) != null) {
                    textParam = embedded.get(i).text();
                }
                var metadata = embedded != null && embedded.get(i) != null
                        ? embedded.get(i).metadata().toMap()
                        : null;

                statement.setString(1, ids.get(i));
                var embeddingsParam = connection.createArrayOf(
                        "float", embeddings.get(i).vectorAsList().toArray());
                statement.setObject(2, embeddingsParam);
                statement.setString(3, textParam);
                statement.setString(4, jsonMetadataSerializer.writeValueAsString(metadata));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException | JsonProcessingException e) {
            throw new DuckDBSQLException("Unable to add embeddings in DuckDB", e);
        }
    }

    private void initTable() {
        var sql = format(CREATE_TABLE_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.createStatement()) {
            log.debug(sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBSQLException(format("Failed to init duckDB table:  '%s'", sql), e);
        }
    }

    protected String embeddingToParam(Embedding embedding) {
        return embedding.vectorAsList().stream()
                .map(Object::toString)
                .collect(Collectors.joining(",", "[", "]"))
                .concat("::float[]");
    }
}
