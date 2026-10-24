package dev.langchain4j.community.store.embedding.arcadedb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps langchain4j {@link Filter} objects to ArcadeDB SQL WHERE clauses with positional parameters ({@code ?}); the
 * values are appended to the parameter list in order, so no value is inlined into the SQL text.
 * Metadata is stored as top-level vertex properties with a configurable prefix.
 */
class ArcadeDBMetadataFilterMapper {

    private final String metadataPrefix;
    // Remote mode: bind Float values as their exact double value (see toParameter).
    private final boolean widenFloats;

    ArcadeDBMetadataFilterMapper(String metadataPrefix, boolean widenFloats) {
        this.metadataPrefix = metadataPrefix;
        this.widenFloats = widenFloats;
    }

    /**
     * Returns the WHERE clause for {@code filter} and appends its parameter values to {@code parameters}.
     */
    String map(Filter filter, List<Object> parameters) {
        return map(filter, value -> {
            parameters.add(value);
            return "?";
        });
    }

    /**
     * Returns the WHERE clause for {@code filter} with named parameters ({@code :f0}, {@code :f1}, ...) and puts their
     * values into {@code parameters}, for queries that already use named parameters.
     */
    String mapNamed(Filter filter, Map<String, Object> parameters) {
        int[] next = {0};
        return map(filter, value -> {
            String name;
            do {
                name = "f" + next[0]++;
            } while (parameters.containsKey(name));
            parameters.put(name, value);
            return ":" + name;
        });
    }

    /** Whether {@code filter} can be mapped to SQL (everything but {@link ContainsString}). */
    static boolean supports(Filter filter) {
        if (filter instanceof And f) {
            return supports(f.left()) && supports(f.right());
        } else if (filter instanceof Or f) {
            return supports(f.left()) && supports(f.right());
        } else if (filter instanceof Not f) {
            return supports(f.expression());
        }
        return filter instanceof IsEqualTo
                || filter instanceof IsNotEqualTo
                || filter instanceof IsGreaterThan
                || filter instanceof IsGreaterThanOrEqualTo
                || filter instanceof IsLessThan
                || filter instanceof IsLessThanOrEqualTo
                || filter instanceof IsIn
                || filter instanceof IsNotIn;
    }

    private String map(Filter filter, Function<Object, String> bind) {
        if (filter instanceof IsEqualTo f) {
            return mapComparison(f.key(), "=", f.comparisonValue(), bind);
        } else if (filter instanceof IsNotEqualTo f) {
            // NULL-safe: property absent means "not equal" is true
            return format(
                    "(%s IS NULL OR %s)", formatKey(f.key()), mapComparison(f.key(), "!=", f.comparisonValue(), bind));
        } else if (filter instanceof IsGreaterThan f) {
            return mapComparison(f.key(), ">", f.comparisonValue(), bind);
        } else if (filter instanceof IsGreaterThanOrEqualTo f) {
            return mapComparison(f.key(), ">=", f.comparisonValue(), bind);
        } else if (filter instanceof IsLessThan f) {
            return mapComparison(f.key(), "<", f.comparisonValue(), bind);
        } else if (filter instanceof IsLessThanOrEqualTo f) {
            return mapComparison(f.key(), "<=", f.comparisonValue(), bind);
        } else if (filter instanceof IsIn f) {
            return format("%s IN %s", formatKey(f.key()), placeholders(f.comparisonValues(), bind));
        } else if (filter instanceof IsNotIn f) {
            // NULL-safe: property absent means "not in" is true
            return format(
                    "(%s IS NULL OR %s NOT IN %s)",
                    formatKey(f.key()), formatKey(f.key()), placeholders(f.comparisonValues(), bind));
        } else if (filter instanceof And f) {
            String left = map(f.left(), bind);
            return format("(%s AND %s)", left, map(f.right(), bind));
        } else if (filter instanceof Or f) {
            String left = map(f.left(), bind);
            return format("(%s OR %s)", left, map(f.right(), bind));
        } else if (filter instanceof Not f) {
            // NULL-safe: if inner expression is NULL (property absent), NOT should be true
            // ArcadeDB doesn't support IFNULL(NOT(...), true), so use "expr IS NULL OR NOT(expr)" pattern.
            // The expression appears twice, so it is mapped twice: its values are bound once per occurrence.
            String inner = map(f.expression(), bind);
            return format("((%s) IS NULL OR NOT(%s))", inner, map(f.expression(), bind));
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapComparison(String key, String operator, Object value, Function<Object, String> bind) {
        return format("%s %s %s", formatKey(key), operator, bind.apply(toParameter(value)));
    }

    String formatKey(String key) {
        return format("`%s%s`", metadataPrefix, key);
    }

    private String placeholders(Collection<?> values, Function<Object, String> bind) {
        return "("
                + values.stream().map(value -> bind.apply(toParameter(value))).collect(Collectors.joining(", ")) + ")";
    }

    private Object toParameter(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        // A float is stored as its exact binary value (1.1f is 1.100000023841858). Over HTTP a Float parameter is sent
        // as its shortest decimal (1.1), a different double, so equality and range filters on floats would miss or
        // wrongly include the stored value; the widened double compares equal to it. Embedded mode binds the Float
        // itself (ArcadeDB 26.9.1's IN does not match a stored Float against a Double).
        if (widenFloats && value instanceof Float f) {
            return f.doubleValue();
        }
        return value;
    }
}
