package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * A specialized ingestor for storing summaries of documents, rather than the full documents, in a Neo4j graph database.
 * It implements the <a href="https://graphrag.com/reference/graphrag/global-community-summary-retriever/">Global Community Summary Retriever concept</a>
 *
 * <p>This ingestor is built on top of {@link Neo4jEmbeddingStoreIngestor} and is designed to process
 * documents into vector embeddings, store them in Neo4j, and generate related hypothetical questions
 * which are also embedded and stored in the graph.
 *
 * <p><strong>It provides:</strong>
 * <ul>
 *     <li>A predefined {@code embeddingStore} configured for child node storage and retrieval</li>
 *     <li>A Cypher {@code creationQuery} and {@code retrievalQuery} for inserting question nodes and linking to parent nodes</li>
 *     <li>A predefined {@code systemPrompt} and {@code userPrompt} for generating summary documents</li>
 * </ul>
 */
public class SummaryGraphIngestor extends Neo4jEmbeddingStoreIngestor {

    public SummaryGraphIngestor(final Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {
        private static final String DEFAULT_RETRIEVAL =
                """
            MATCH (node)<-[:HAS_SUMMARY]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        private static final String DEFAULT_SYSTEM_PROMPT =
                """
            You are generating concise and accurate summaries based on the information found in the text.
            """;

        private static final String DEFAULT_USER_PROMPT =
                """
            Generate a summary of the following input:
            {{input}}

            Summary:
            """;

        private static final String DEFAULT_PARENT_QUERY =
                """
                    UNWIND $rows AS row
                    MATCH (p:SummaryChunk {parentId: $parentId})
                    CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";
        private static final String DEFAULT_CHUNK_CREATION_QUERY = "CREATE (:SummaryChunk $metadata)";

        private Neo4jEmbeddingStore defaultEmbeddingStore() {
            return Neo4jEmbeddingStore.builder()
                    .driver(driver)
                    .retrievalQuery(DEFAULT_RETRIEVAL)
                    .entityCreationQuery(DEFAULT_PARENT_QUERY)
                    .label("Summary")
                    .indexName("summary_embedding_index")
                    .dimension(384)
                    .build();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SummaryGraphIngestor build() {
            systemPrompt = getOrDefault(systemPrompt, DEFAULT_SYSTEM_PROMPT);
            userPrompt = getOrDefault(userPrompt, DEFAULT_USER_PROMPT);
            query = getOrDefault(query, DEFAULT_CHUNK_CREATION_QUERY);
            embeddingStore = getOrDefault(embeddingStore, defaultEmbeddingStore());

            return new SummaryGraphIngestor(this);
        }
    }
}
