package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * A specialized ingestor for storing document-linked embedded chunks that represent specific concepts,
 * and associating them with parent documents in a Neo4j graph database.
 * It implements the <a href="https://graphrag.com/reference/graphrag/parent-child-retriever/">Parent-Child Retriever concept</a>
 *
 * <p>This ingestor is built on top of {@link Neo4jEmbeddingStoreIngestor} and is designed to process
 * documents into vector embeddings, store them in Neo4j, and generate related hypothetical questions
 * which are also embedded and stored in the graph.
 *
 * <p><strong>It provides:</strong>
 * <ul>
 *     <li>A predefined {@code embeddingStore} configured for child node storage and retrieval</li>
 *     <li>A Cypher {@code creationQuery} and {@code retrievalQuery} for inserting question nodes and linking to parent nodes</li>
 * </ul>
 */
public class ParentChildGraphIngestor extends Neo4jEmbeddingStoreIngestor {

    public ParentChildGraphIngestor(final Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {
        private static final String DEFAULT_RETRIEVAL =
                """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        private static final String DEFAULT_PARENT_QUERY =
                """
                    UNWIND $rows AS row
                    MATCH (p:ParentChunk {parentId: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";
        private static final String DEFAULT_CHUNK_CREATION_QUERY = "CREATE (:ParentChunk $metadata)";

        private EmbeddingStore defaultEmbeddingStore() {
            return Neo4jEmbeddingStore.builder()
                    .driver(driver)
                    .retrievalQuery(DEFAULT_RETRIEVAL)
                    .entityCreationQuery(DEFAULT_PARENT_QUERY)
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
        public ParentChildGraphIngestor build() {
            query = getOrDefault(query, DEFAULT_CHUNK_CREATION_QUERY);
            embeddingStore = getOrDefault(embeddingStore, defaultEmbeddingStore());

            return new ParentChildGraphIngestor(this);
        }
    }
}
