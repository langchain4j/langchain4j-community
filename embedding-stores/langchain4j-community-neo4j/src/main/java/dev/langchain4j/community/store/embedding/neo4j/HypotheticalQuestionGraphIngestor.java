package dev.langchain4j.community.store.embedding.neo4j;

/**
 * TODO: graph rag website
 */
public class HypotheticalQuestionGraphIngestor extends Neo4jEmbeddingStoreIngestor {

    public HypotheticalQuestionGraphIngestor(final Neo4jIngestorConfig config) {
        super(config);
    }

    public static SummaryGraphIngestor.Builder builder() {
        return new SummaryGraphIngestor.Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {

        public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_QUESTION]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
             ORDER BY score DESC
            LIMIT $maxResults""";

        public static final String PARENT_QUERY =
                """
                            UNWIND $rows AS question
                            MATCH (p:QuestionChunk {parentId: $parentId})
                            WITH p, question
                            CREATE (q:%1$s {%2$s: question.%2$s})
                            SET q += question.%3$s
                            MERGE (q)<-[:HAS_QUESTION]-(p)
                            WITH q, question
                            CALL db.create.setNodeVectorProperty(q, $embeddingProperty, question.%4$s)
                            RETURN count(*)
                        """;

        public final static String SYSTEM_PROMPT = """
            You are generating hypothetical questions based on the information found in the text.
            Make sure to provide full context in the generated questions.
            """;

        public final static String USER_PROMPT = """
            Use the given format to generate hypothetical questions from the following input:
            {{input}}

            Hypothetical questions:
            """;
        
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
            return "CREATE (:QuestionChunk $metadata)";
        }

        @Override
        protected Neo4jEmbeddingStore getEmbeddingStore() {
            return Neo4jEmbeddingStore.builder()
                    .driver(driver)
                    .retrievalQuery(DEFAULT_RETRIEVAL)
                    .entityCreationQuery(PARENT_QUERY)
                    .label("Child")
                    .indexName("child_embedding_index")
                    .dimension(384)
                    .build();
        }
        
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public HypotheticalQuestionGraphIngestor build() {
            return new HypotheticalQuestionGraphIngestor(createIngestorConfig());
        }
    }

}
