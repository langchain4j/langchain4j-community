package dev.langchain4j.community.store.embedding.arcadedb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps langchain4j {@link Filter} objects to ArcadeDB SQL WHERE clauses with positional parameters ({@code ?}); the
 * values are appended to the parameter list in order, so no value is inlined into the SQL text.
 * Metadata is stored as top-level vertex properties with a configurable prefix.
 */
class ArcadeDBMetadataFilterMapper {

    private final String metadataPrefix;

    ArcadeDBMetadataFilterMapper(String metadataPrefix) {
        this.metadataPrefix = metadataPrefix;
    }

    /**
     * Returns the WHERE clause for {@code filter} and appends its parameter values to {@code parameters}.
     */
    String map(Filter filter, List<Object> parameters) {
        if (filter instanceof IsEqualTo f) {
            return mapComparison(f.key(), "=", f.comparisonValue(), parameters);
        } else if (filter instanceof IsNotEqualTo f) {
            // NULL-safe: property absent means "not equal" is true
            return format(
                    "(%s IS NULL OR %s)",
                    formatKey(f.key()), mapComparison(f.key(), "!=", f.comparisonValue(), parameters));
        } else if (filter instanceof IsGreaterThan f) {
            return mapComparison(f.key(), ">", f.comparisonValue(), parameters);
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            return mapComparison(f.key(), ">=", f.comparisonValue(), parameters);
        } else if (filter instanceof IsLessThan f) {
            return mapComparison(f.key(), "<", f.comparisonValue(), parameters);
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            return mapComparison(f.key(), "<=", f.comparisonValue(), parameters);
        } else if (filter instanceof IsIn f) {
            return format("%s IN %s", formatKey(f.key()), placeholders(f.comparisonValues(), parameters));
        } else if (filter instanceof IsNotIn f) {
            // NULL-safe: property absent means "not in" is true
            return format(
                    "(%s IS NULL OR %s NOT IN %s)",
                    formatKey(f.key()), formatKey(f.key()), placeholders(f.comparisonValues(), parameters));
        } else if (filter instanceof And f) {
            String left = map(f.left(), parameters);
            return format("(%s AND %s)", left, map(f.right(), parameters));
        } else if (filter instanceof Or f) {
            String left = map(f.left(), parameters);
            return format("(%s OR %s)", left, map(f.right(), parameters));
        } else if (filter instanceof Not f) {
            // NULL-safe: if inner expression is NULL (property absent), NOT should be true
            // ArcadeDB doesn't support IFNULL(NOT(...), true), so use "expr IS NULL OR NOT(expr)" pattern.
            // The expression appears twice, so its parameters are appended twice.
            String inner = map(f.expression(), parameters);
            map(f.expression(), parameters);
            return format("((%s) IS NULL OR NOT(%s))", inner, inner);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapComparison(String key, String operator, Object value, List<Object> parameters) {
        parameters.add(toParameter(value));
        return format("%s %s ?", formatKey(key), operator);
    }

    String formatKey(String key) {
        return format("`%s%s`", metadataPrefix, key);
    }

    private static String placeholders(Collection<?> values, List<Object> parameters) {
        values.forEach(value -> parameters.add(toParameter(value)));
        return "(" + values.stream().map(value -> "?").collect(Collectors.joining(", ")) + ")";
    }

    private static Object toParameter(Object value) {
        return value instanceof UUID uuid ? uuid.toString() : value;
    }
}
