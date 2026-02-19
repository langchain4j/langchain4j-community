package dev.langchain4j.community.store.embedding.neo4j;

import static org.neo4j.cypherdsl.core.Cypher.match;
import static org.neo4j.cypherdsl.core.Cypher.node;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.cypherdsl.core.Condition;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

public class MatchSearchClauseStrategy implements SearchStrategy {

    @Override
    public boolean usesCypher25() {
        return true;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(
            Neo4jEmbeddingStore store, EmbeddingSearchRequest request, Value embeddingValue, Session session) {
        String filterClause = "";

        if (request.filter() != null) {
            var node = node(store.getLabel()).named("node");
            var mapper = new Neo4jFilterMapper(node);
            Condition condition = mapper.getCondition(request.filter());

            Statement conditionStatement =
                    match(node).where(condition).returning(node).build();
            String renderedStatement = Renderer.getDefaultRenderer().render(conditionStatement);

            int whereIndex = renderedStatement.indexOf("WHERE");
            if (whereIndex != -1) {
                int returnIndex = renderedStatement.lastIndexOf("RETURN");
                if (returnIndex > whereIndex) {
                    filterClause =
                            renderedStatement.substring(whereIndex, returnIndex).trim();
                } else {
                    filterClause = renderedStatement.substring(whereIndex).trim();
                }
                // GQL SEARCH clause requires dot notation (node.prop) instead of bracket notation (node['prop'])
                filterClause = filterClause.replaceAll("\\['([a-zA-Z0-9_]+)'\\]", ".$1");
            }
        }

        String gqlQuery = String.format(
                """
                CYPHER 25
                MATCH (node)
                SEARCH node IN (
                   VECTOR INDEX %s
                   FOR $embeddingValue
                   %s
                   LIMIT $maxResults
                ) SCORE AS score
                %s
                """,
                store.getIndexName(), filterClause, store.getRetrievalQuery());

        Map<String, Object> params = new HashMap<>();
        params.put("embeddingValue", embeddingValue);
        params.put("maxResults", request.maxResults());

        return store.getEmbeddingSearchResult(session, gqlQuery, params);
    }
}
