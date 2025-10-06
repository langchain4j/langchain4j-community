package dev.langchain4j.community.store.embedding.yugabytedb;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Maps LangChain4j Filter objects to YugabyteDB SQL WHERE clauses with parameterized queries.
 *
 * This mapper converts the filter tree into PostgreSQL-compatible JSON queries
 * since YugabyteDB supports JSONB operations similar to PostgreSQL.
 * Uses parameterized queries to prevent SQL injection attacks.
 */
public class YugabyteDBMetadataFilterMapper {

    /**
     * Result of filter mapping containing SQL clause and parameter values
     */
    public static class FilterResult {
        private final String sqlClause;
        private final List<Object> parameters;

        public FilterResult(String sqlClause, List<Object> parameters) {
            this.sqlClause = sqlClause;
            this.parameters = parameters;
        }

        public String getSqlClause() {
            return sqlClause;
        }

        public List<Object> getParameters() {
            return parameters;
        }
    }

    /**
     * Maps a Filter to a SQL WHERE clause with parameters for YugabyteDB
     *
     * @param filter the filter to map
     * @return FilterResult containing SQL clause and parameters
     */
    public FilterResult map(Filter filter) {
        if (filter == null) {
            return new FilterResult("", new ArrayList<>());
        }
        return mapFilter(filter);
    }

    /**
     * Sets parameters in the prepared statement based on filter mapping
     *
     * @param statement the prepared statement
     * @param filter the filter
     * @param startIndex the starting parameter index
     * @return the next parameter index
     * @throws SQLException if parameter setting fails
     */
    public int setFilterParameters(PreparedStatement statement, Filter filter, int startIndex) throws SQLException {
        FilterResult result = map(filter);
        int paramIndex = startIndex;

        for (Object param : result.getParameters()) {
            if (param instanceof String) {
                statement.setString(paramIndex, (String) param);
            } else if (param instanceof Integer) {
                statement.setInt(paramIndex, (Integer) param);
            } else if (param instanceof Long) {
                statement.setLong(paramIndex, (Long) param);
            } else if (param instanceof Double) {
                statement.setDouble(paramIndex, (Double) param);
            } else if (param instanceof Float) {
                statement.setFloat(paramIndex, (Float) param);
            } else if (param instanceof Boolean) {
                statement.setBoolean(paramIndex, (Boolean) param);
            } else if (param instanceof UUID) {
                statement.setObject(paramIndex, param);
            } else {
                statement.setString(paramIndex, param.toString());
            }
            paramIndex++;
        }

        return paramIndex;
    }

    private FilterResult mapFilter(Filter filter) {
        if (filter instanceof ContainsString) {
            return mapContainsString((ContainsString) filter);
        } else if (filter instanceof IsEqualTo) {
            return mapIsEqualTo((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapIsNotEqualTo((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return mapIsGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapIsGreaterThanOrEqualTo((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return mapIsLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapIsLessThanOrEqualTo((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return mapIsIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return mapIsNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getSimpleName());
        }
    }

    private FilterResult mapContainsString(ContainsString filter) {
        String clause = String.format(
                "metadata->>'%s' IS NOT NULL AND metadata->>'%s' ~ ?",
                sanitizeKey(filter.key()), sanitizeKey(filter.key()));
        List<Object> params = new ArrayList<>();
        params.add(filter.comparisonValue().toString());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsEqualTo(IsEqualTo filter) {
        String clause = String.format(
                "metadata->>'%s' IS NOT NULL AND metadata->>'%s' = ?",
                sanitizeKey(filter.key()), sanitizeKey(filter.key()));
        List<Object> params = new ArrayList<>();
        params.add(filter.comparisonValue());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsNotEqualTo(IsNotEqualTo filter) {
        String clause = String.format(
                "metadata->>'%s' IS NULL OR metadata->>'%s' != ?",
                sanitizeKey(filter.key()), sanitizeKey(filter.key()));
        List<Object> params = new ArrayList<>();
        params.add(filter.comparisonValue());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsGreaterThan(IsGreaterThan filter) {
        String key = sanitizeKey(filter.key());
        String clause;
        List<Object> params = new ArrayList<>();

        if (filter.comparisonValue() instanceof Number) {
            clause = String.format("(metadata->>'%s')::numeric > ?", key);
        } else {
            clause = String.format("metadata->>'%s' > ?", key);
        }
        params.add(filter.comparisonValue());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        String key = sanitizeKey(filter.key());
        String clause;
        List<Object> params = new ArrayList<>();

        if (filter.comparisonValue() instanceof Number) {
            clause = String.format("(metadata->>'%s')::numeric >= ?", key);
        } else {
            clause = String.format("metadata->>'%s' >= ?", key);
        }
        params.add(filter.comparisonValue());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsLessThan(IsLessThan filter) {
        String key = sanitizeKey(filter.key());
        String clause;
        List<Object> params = new ArrayList<>();

        if (filter.comparisonValue() instanceof Number) {
            clause = String.format("(metadata->>'%s')::numeric < ?", key);
        } else {
            clause = String.format("metadata->>'%s' < ?", key);
        }
        params.add(filter.comparisonValue());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        String key = sanitizeKey(filter.key());
        String clause;
        List<Object> params = new ArrayList<>();

        if (filter.comparisonValue() instanceof Number) {
            clause = String.format("(metadata->>'%s')::numeric <= ?", key);
        } else {
            clause = String.format("metadata->>'%s' <= ?", key);
        }
        params.add(filter.comparisonValue());
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsIn(IsIn filter) {
        Collection<?> values = filter.comparisonValues();
        if (values.isEmpty()) {
            return new FilterResult("FALSE", new ArrayList<>());
        }

        String key = sanitizeKey(filter.key());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }

        String clause = String.format("metadata->>'%s' IN (%s)", key, placeholders.toString());
        List<Object> params = new ArrayList<>(values);
        return new FilterResult(clause, params);
    }

    private FilterResult mapIsNotIn(IsNotIn filter) {
        Collection<?> values = filter.comparisonValues();
        if (values.isEmpty()) {
            return new FilterResult("TRUE", new ArrayList<>());
        }

        String key = sanitizeKey(filter.key());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }

        String clause = String.format(
                "metadata->>'%s' IS NULL OR metadata->>'%s' NOT IN (%s)", key, key, placeholders.toString());
        List<Object> params = new ArrayList<>(values);
        return new FilterResult(clause, params);
    }

    private FilterResult mapAnd(And filter) {
        FilterResult left = mapFilter(filter.left());
        FilterResult right = mapFilter(filter.right());

        String clause = String.format("(%s AND %s)", left.getSqlClause(), right.getSqlClause());
        List<Object> params = new ArrayList<>(left.getParameters());
        params.addAll(right.getParameters());

        return new FilterResult(clause, params);
    }

    private FilterResult mapOr(Or filter) {
        FilterResult left = mapFilter(filter.left());
        FilterResult right = mapFilter(filter.right());

        String clause = String.format("(%s OR %s)", left.getSqlClause(), right.getSqlClause());
        List<Object> params = new ArrayList<>();
        for (Object param : left.getParameters()) {
            params.add(param);
        }
        for (Object param : right.getParameters()) {
            params.add(param);
        }

        return new FilterResult(clause, params);
    }

    private FilterResult mapNot(Not filter) {
        FilterResult result = mapFilter(filter.expression());
        String clause = String.format("NOT (%s)", result.getSqlClause());
        return new FilterResult(clause, result.getParameters());
    }

    /**
     * Sanitizes metadata keys to prevent injection through key names
     * Only allows alphanumeric characters, underscores, and dots
     */
    private String sanitizeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Metadata key cannot be null");
        }

        // Only allow alphanumeric, underscore, and dot characters
        if (!key.matches("^[a-zA-Z0-9_.]+$")) {
            throw new IllegalArgumentException("Invalid metadata key: " + key
                    + ". Only alphanumeric characters, underscores, and dots are allowed.");
        }

        return key;
    }
}
