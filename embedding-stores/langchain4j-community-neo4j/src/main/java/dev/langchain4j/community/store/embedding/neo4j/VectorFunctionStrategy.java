package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jFilterMapper.toCypherLiteral;
import static dev.langchain4j.community.store.embedding.neo4j.Neo4jUtils.functionDef;
import static org.neo4j.cypherdsl.core.Cypher.call;
import static org.neo4j.cypherdsl.core.Cypher.mapOf;
import static org.neo4j.cypherdsl.core.Cypher.match;
import static org.neo4j.cypherdsl.core.Cypher.name;
import static org.neo4j.cypherdsl.core.Cypher.node;
import static org.neo4j.cypherdsl.core.Cypher.parameter;
import static org.neo4j.cypherdsl.core.Cypher.raw;
import static org.neo4j.cypherdsl.core.Cypher.size;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

public class VectorFunctionStrategy implements SearchStrategy {

    @Override
    public EmbeddingSearchResult<TextSegment> search(
            Neo4jEmbeddingStore store, EmbeddingSearchRequest request, Value embeddingValue, Session session) {
        Filter filter = request.filter();
        if (filter == null) {
            return searchUsingVectorIndex(store, request, embeddingValue, session);
        }
        return searchUsingVectorSimilarity(store, request, filter, embeddingValue, session);
    }

    private EmbeddingSearchResult<TextSegment> searchUsingVectorSimilarity(
            Neo4jEmbeddingStore store,
            EmbeddingSearchRequest request,
            Filter filter,
            Value embeddingValue,
            Session session) {
        var node = node(store.getLabel()).named("node");
        var neo4jFilterMapper = new Neo4jFilterMapper(node);

        var condition = node.property(store.getEmbeddingProperty())
                .isNotNull()
                .and(size(node.property(store.getEmbeddingProperty())).eq(toCypherLiteral(store.getDimension())))
                .and(neo4jFilterMapper.getCondition(filter));

        var similarity = org.neo4j.cypherdsl.core.FunctionInvocation.create(
                functionDef("vector.similarity.cosine"),
                node.property(store.getEmbeddingProperty()),
                toCypherLiteral(embeddingValue));

        var statement = match(node)
                .where(condition)
                .with(node.as("node"), similarity.as("score"))
                .where(similarity.gte(parameter("minScore")))
                .returning(raw(store.getRetrievalQuery()))
                .orderBy(name("score"))
                .descending()
                .limit(parameter("maxResults"))
                .build();

        String cypherQuery = store.getRender(statement);
        Map<String, Object> params = new HashMap<>();
        params.put("minScore", request.minScore());
        params.put("maxResults", request.maxResults());

        return store.getEmbeddingSearchResult(session, cypherQuery, params);
    }

    private EmbeddingSearchResult<TextSegment> searchUsingVectorIndex(
            Neo4jEmbeddingStore store, EmbeddingSearchRequest request, Value embeddingValue, Session session) {
        var indexNameParam = parameter("indexName");
        var maxResultsParam = parameter("maxResults");
        var embeddingValueParam = parameter("embeddingValue");
        var minScoreParam = parameter("minScore");

        Statement vectorQuery = call("db.index.vector.queryNodes")
                .withArgs(indexNameParam, maxResultsParam, embeddingValueParam)
                .yield("node", "score")
                .where(name("score").gte(minScoreParam))
                .returning(raw(store.getRetrievalQuery()))
                .build();

        Statement statement;
        Map<String, Object> params = new HashMap<>();
        params.put("indexName", store.getIndexName());
        params.put("embeddingValue", embeddingValue);
        params.put("minScore", request.minScore());
        params.put("maxResults", request.maxResults());

        if (store.getFullTextQuery() != null) {
            var fullTextIndexNameParam = parameter("fullTextIndexName");
            var fullTextQueryParam = parameter("fullTextQuery");

            Statement fullTextSearch = call("db.index.fulltext.queryNodes")
                    .withArgs(fullTextIndexNameParam, fullTextQueryParam, mapOf("limit", maxResultsParam))
                    .yield("node", "score")
                    .where(name("score").gte(minScoreParam))
                    .returning(raw(store.getFullTextRetrievalQuery()))
                    .build();

            statement = Cypher.union(vectorQuery, fullTextSearch);
            params.put("fullTextIndexName", store.getFullTextIndexName());
            params.put("fullTextQuery", store.getFullTextQuery());
        } else {
            statement = vectorQuery;
        }

        String cypherQuery = store.getRender(statement);
        store.validateColumns(session, cypherQuery);

        return store.getEmbeddingSearchResult(session, cypherQuery, params);
    }
}
