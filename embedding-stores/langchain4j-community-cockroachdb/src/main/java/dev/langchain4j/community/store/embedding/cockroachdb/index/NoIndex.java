package dev.langchain4j.community.store.embedding.cockroachdb.index;

import java.util.List;

/**
 * Sentinel that suppresses index creation; CockroachDB will fall back to a
 * sequential scan for similarity search. Reasonable for small datasets and tests.
 */
public class NoIndex implements BaseIndex {

    @Override
    public String getCreateIndexSql(String fullyQualifiedTable, String embeddingColumn, List<String> prefixColumns) {
        return null;
    }

    @Override
    public String getName(String tableName, String embeddingColumn) {
        return null;
    }

    @Override
    public String toString() {
        return "NoIndex (sequential scan)";
    }
}
