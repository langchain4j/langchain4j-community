package dev.langchain4j.community.store.embedding.neo4j;


public class ParentChildGraphIngestor extends Neo4jEmbeddingStoreIngestor {
    

    public ParentChildGraphIngestor(final Neo4jIngestorConfig config) {
        super(config);
    }

    //    public ParentChildGraphIngestor(EmbeddingModel embeddingModel, Driver driver,
//                                     int maxResults,
//                                     double minScore, Neo4jEmbeddingStore embeddingStore) {
//        super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore,  null, null, null, null, null, null);
//    }

    // @Override


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {
        public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        public static final String PARENT_QUERY =
                """
                    UNWIND $rows AS row
                    MATCH (p:ParentChunk {parentId: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";
        
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
        protected String getQuery() {
            return "CREATE (:ParentChunk $metadata)";
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ParentChildGraphIngestor build() {
            return new ParentChildGraphIngestor(createIngestorConfig());
        }
    }


}
