package dev.langchain4j.community.store.embedding.cockroachdb.index;

import java.util.List;

/**
 * Strategy for the vector index created alongside a CockroachDB embedding table.
 *
 * <p>CockroachDB's vector index DDL ({@code CREATE VECTOR INDEX}) is distinct from
 * pgvector — there is no {@code USING}, no opclass, and the distance metric is
 * selected at query time by the operator rather than bound to the index. Each
 * implementation owns its full DDL string.
 */
public interface BaseIndex {

    /**
     * Build the {@code CREATE INDEX} (or {@code CREATE VECTOR INDEX}) statement
     * for this strategy.
     *
     * @param fullyQualifiedTable schema-qualified table name (e.g. {@code public.embeddings})
     * @param embeddingColumn     name of the vector column
     * @param prefixColumns       columns to place before the vector column for
     *                            multi-tenant / partitioned indexes (may be empty)
     * @return DDL statement, or {@code null} if no index should be created
     */
    String getCreateIndexSql(String fullyQualifiedTable, String embeddingColumn, List<String> prefixColumns);

    /**
     * Resolved index name, or {@code null} if no index will be created.
     */
    String getName(String tableName, String embeddingColumn);
}
