package dev.langchain4j.community.store.embedding.neo4j;

/**
 *  A specialized ingestor for generating and storing hypothetical questions in a Neo4j graph database.
 * It implements the <a href="https://graphrag.com/reference/graphrag/hypothetical-question-retriever/">Hypothetical Question Retriever concept</a>
 *
 * <p>This ingestor is built on top of {@link Neo4jEmbeddingStoreIngestor} and is designed to process
 * documents into vector embeddings, store them in Neo4j, and generate related hypothetical questions
 * which are also embedded and stored in the graph.
 *
 * <p><strong>It provides:</strong>
 * <ul>
 *     <li>A predefined {@code embeddingStore} configured for child node storage and retrieval</li>
 *     <li>A Cypher {@code creationQuery} and {@code retrievalQuery} for inserting question nodes and linking to parent nodes</li>
 *     <li>A predefined {@code systemPrompt} and {@code userPrompt} for generating hypothetical questions</li>
 * </ul>
 */
public class HypotheticalQuestionGraphIngestor extends Neo4jEmbeddingStoreIngestor {

    public HypotheticalQuestionGraphIngestor(final Neo4jIngestorConfig config) {
        super(config);
    }

    public static SummaryGraphIngestor.Builder builder() {
        return new SummaryGraphIngestor.Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {

        private static final String RETRIEVAL_QUERY =
                """
            MATCH (node)<-[:HAS_QUESTION]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
             ORDER BY score DESC
            LIMIT $maxResults""";

        private static final String CREATION_QUERY =
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

        private static final String SYSTEM_PROMPT =
                """
            You are generating hypothetical questions based on the information found in the text.
            Make sure to provide full context in the generated questions.
            """;

        private static final String USER_PROMPT =
                """
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
                    .retrievalQuery(RETRIEVAL_QUERY)
                    .entityCreationQuery(CREATION_QUERY)
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
