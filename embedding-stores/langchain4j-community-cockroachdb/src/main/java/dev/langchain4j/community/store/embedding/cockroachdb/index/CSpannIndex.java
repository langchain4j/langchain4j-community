package dev.langchain4j.community.store.embedding.cockroachdb.index;

import java.util.ArrayList;
import java.util.List;

/**
 * CockroachDB's distributed approximate-nearest-neighbour vector index (C-SPANN),
 * available from CockroachDB v25.2.
 *
 * <p>Emits the form:
 * <pre>
 *   CREATE VECTOR INDEX IF NOT EXISTS &lt;name&gt;
 *     ON &lt;table&gt; ([prefix_cols, ...] &lt;embedding_col&gt;)
 *     [WITH (min_partition_size = N, max_partition_size = M)]
 * </pre>
 *
 * <p>{@code min_partition_size} and {@code max_partition_size} are the only
 * options recognised by the Python reference library; surface them as builder
 * fields and leave them {@code null} to let CockroachDB pick defaults.
 *
 * <p>{@code prefixColumns} go <em>before</em> the embedding column for
 * multi-tenant scoping (e.g. {@code (tenant_id, embedding)}).
 *
 * <p>Note: the distance metric is NOT part of the index. CockroachDB infers it
 * from the operator ({@code <->}, {@code <=>}, {@code <#>}) at query time.
 */
public class CSpannIndex implements BaseIndex {

    private final String name;
    private final Integer minPartitionSize;
    private final Integer maxPartitionSize;

    private CSpannIndex(Builder builder) {
        this.name = builder.name;
        this.minPartitionSize = builder.minPartitionSize;
        this.maxPartitionSize = builder.maxPartitionSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String stripSchema(String fqn) {
        int dot = fqn.indexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    @Override
    public String getCreateIndexSql(String fullyQualifiedTable, String embeddingColumn, List<String> prefixColumns) {
        String resolvedName = getName(stripSchema(fullyQualifiedTable), embeddingColumn);

        List<String> columns = new ArrayList<>();
        if (prefixColumns != null) {
            columns.addAll(prefixColumns);
        }
        columns.add(embeddingColumn);

        StringBuilder sql = new StringBuilder()
                .append("CREATE VECTOR INDEX IF NOT EXISTS ")
                .append(resolvedName)
                .append(" ON ")
                .append(fullyQualifiedTable)
                .append(" (")
                .append(String.join(", ", columns))
                .append(")");

        List<String> withClauses = new ArrayList<>();
        if (minPartitionSize != null) {
            withClauses.add("min_partition_size = " + minPartitionSize);
        }
        if (maxPartitionSize != null) {
            withClauses.add("max_partition_size = " + maxPartitionSize);
        }
        if (!withClauses.isEmpty()) {
            sql.append(" WITH (").append(String.join(", ", withClauses)).append(")");
        }
        return sql.toString();
    }

    @Override
    public String getName(String tableName, String embeddingColumn) {
        if (name != null && !name.isEmpty()) return name;
        return tableName + "_" + embeddingColumn + "_vector_idx";
    }

    public Integer getMinPartitionSize() {
        return minPartitionSize;
    }

    public Integer getMaxPartitionSize() {
        return maxPartitionSize;
    }

    public static class Builder {
        private String name;
        private Integer minPartitionSize;
        private Integer maxPartitionSize;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder minPartitionSize(Integer minPartitionSize) {
            if (minPartitionSize != null && minPartitionSize <= 0) {
                throw new IllegalArgumentException("minPartitionSize must be positive");
            }
            this.minPartitionSize = minPartitionSize;
            return this;
        }

        public Builder maxPartitionSize(Integer maxPartitionSize) {
            if (maxPartitionSize != null && maxPartitionSize <= 0) {
                throw new IllegalArgumentException("maxPartitionSize must be positive");
            }
            this.maxPartitionSize = maxPartitionSize;
            return this;
        }

        public CSpannIndex build() {
            if (minPartitionSize != null && maxPartitionSize != null && minPartitionSize > maxPartitionSize) {
                throw new IllegalStateException("minPartitionSize must be <= maxPartitionSize");
            }
            return new CSpannIndex(this);
        }
    }
}
