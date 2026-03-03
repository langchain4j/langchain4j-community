package dev.langchain4j.community.store.embedding.arcadedb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps langchain4j {@link Filter} objects to ArcadeDB SQL WHERE clauses.
 * Metadata is stored as top-level vertex properties with a configurable prefix.
 */
class ArcadeDBMetadataFilterMapper {

    private final String metadataPrefix;

    ArcadeDBMetadataFilterMapper(String metadataPrefix) {
        this.metadataPrefix = metadataPrefix;
    }

    String map(Filter filter) {
        if (filter instanceof IsEqualTo f) {
            return mapComparison(f.key(), "=", f.comparisonValue());
        } else if (filter instanceof IsNotEqualTo f) {
            // NULL-safe: property absent means "not equal" is true
            return format("(%s IS NULL OR %s)", formatKey(f.key()), mapComparison(f.key(), "!=", f.comparisonValue()));
        } else if (filter instanceof IsGreaterThan f) {
            return mapComparison(f.key(), ">", f.comparisonValue());
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            return mapComparison(f.key(), ">=", f.comparisonValue());
        } else if (filter instanceof IsLessThan f) {
            return mapComparison(f.key(), "<", f.comparisonValue());
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            return mapComparison(f.key(), "<=", f.comparisonValue());
        } else if (filter instanceof IsIn f) {
            return mapIn(f);
        } else if (filter instanceof IsNotIn f) {
            return mapNotIn(f);
        } else if (filter instanceof And f) {
            return format("(%s AND %s)", map(f.left()), map(f.right()));
        } else if (filter instanceof Or f) {
            return format("(%s OR %s)", map(f.left()), map(f.right()));
        } else if (filter instanceof Not f) {
            // NULL-safe: if inner expression is NULL (property absent), NOT should be true
            // ArcadeDB doesn't support IFNULL(NOT(...), true), so use "expr IS NULL OR NOT(expr)" pattern
            String inner = map(f.expression());
            return format("((%s) IS NULL OR NOT(%s))", inner, inner);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapComparison(String key, String operator, Object value) {
        return format("%s %s %s", formatKey(key), operator, formatValue(value));
    }

    private String mapIn(IsIn filter) {
        return format("%s IN %s", formatKey(filter.key()), formatValues(filter.comparisonValues()));
    }

    private String mapNotIn(IsNotIn filter) {
        // NULL-safe: property absent means "not in" is true
        return format("(%s IS NULL OR %s NOT IN %s)", formatKey(filter.key()), formatKey(filter.key()),
                formatValues(filter.comparisonValues()));
    }

    String formatKey(String key) {
        return format("`%s%s`", metadataPrefix, key);
    }

    String formatValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "'" + escapeString(value.toString()) + "'";
        } else {
            return value.toString();
        }
    }

    String formatValues(Collection<?> values) {
        return "(" + values.stream().map(this::formatValue).collect(Collectors.joining(", ")) + ")";
    }

    private static String escapeString(String value) {
        return value.replace("'", "\\'");
    }
}
