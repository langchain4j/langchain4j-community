package dev.langchain4j.community.store.embedding.neo4j;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

public interface SearchStrategy {

    /**
     * Executes the search logic.
     */
    EmbeddingSearchResult<TextSegment> search(
            Neo4jEmbeddingStore store, EmbeddingSearchRequest request, Value embeddingValue, Session session);

    /**
     * Indicates if this strategy requires the CYPHER 25 prefix
     * Used during index creation.
     */
    default boolean usesCypher25() {
        return false;
    }
}
