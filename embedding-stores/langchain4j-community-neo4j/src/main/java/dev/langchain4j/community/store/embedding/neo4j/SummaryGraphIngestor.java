package dev.langchain4j.community.store.embedding.neo4j;

/**
 * TODO: graphrag website
 */
public class SummaryGraphIngestor extends Neo4jEmbeddingStoreIngestor {

    public SummaryGraphIngestor(final Neo4jIngestorConfig config) {
        super(config);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {
        private final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_SUMMARY]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        private final static String SYSTEM_PROMPT = """
            You are generating concise and accurate summaries based on the information found in the text.
            """;

        private final static String USER_PROMPT = """
            Generate a summary of the following input:
            {{input}}
            
            Summary:
            """;

        private static final String PARENT_QUERY =
                """
                    UNWIND $rows AS row
                    MATCH (p:SummaryChunk {parentId: $parentId})
                    CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";
        
        @Override
        protected String getSystemPrompt() {
            return SYSTEM_PROMPT;
        }

        @Override
        protected String getUserPrompt() {
            return USER_PROMPT;
        }

        @Override
        protected String getQuery() {
            return "CREATE (:SummaryChunk $metadata)";
        }

        @Override
        protected Neo4jEmbeddingStore getEmbeddingStore() {
            return Neo4jEmbeddingStore.builder()
                    .driver(driver)
                    .retrievalQuery(DEFAULT_RETRIEVAL)
                    .entityCreationQuery(PARENT_QUERY)
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
            return new SummaryGraphIngestor(createIngestorConfig());
        }
    }
}
