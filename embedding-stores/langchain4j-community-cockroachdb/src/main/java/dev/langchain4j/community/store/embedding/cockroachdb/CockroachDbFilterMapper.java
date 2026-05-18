package dev.langchain4j.community.store.embedding.cockroachdb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
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
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Translates LangChain4j {@link Filter}s into CockroachDB WHERE-clause fragments
 * against a JSONB metadata column.
 *
 * <p>Numeric comparisons use {@code (metadata->>'key')::numeric}; string equality
 * uses {@code metadata->>'key' = '…'}; {@code IN} uses {@code metadata->>'key' IN ('…','…')}.
 * Identifiers from filter keys are sanitised to alphanumeric + {@code . _ -}.
 *
 * <p>This implementation inlines values into the SQL string (similar to the Python
 * reference library). Filter values come from application code, not user input, but
 * key sanitisation is enforced regardless.
 */
class CockroachDbFilterMapper {

    private static final Map<Class<?>, String> SQL_TYPE_MAP = Map.of(
            Integer.class, "int",
            Long.class, "bigint",
            Float.class, "float",
            Double.class, "float8",
            String.class, "text",
            UUID.class, "uuid",
            Boolean.class, "boolean");

    private final String metadataColumn;

    CockroachDbFilterMapper(String metadataColumn) {
        if (metadataColumn == null || metadataColumn.trim().isEmpty()) {
            throw new IllegalArgumentException("metadataColumn must not be empty");
        }
        this.metadataColumn = metadataColumn.trim();
    }

    String map(Filter filter) {
        if (filter instanceof IsEqualTo f) {
            String key = formatKey(f.key(), f.comparisonValue().getClass());
            return format("%s is not null and %s = %s", key, key, formatValue(f.comparisonValue()));
        }
        if (filter instanceof IsNotEqualTo f) {
            String key = formatKey(f.key(), f.comparisonValue().getClass());
            return format("%s is null or %s != %s", key, key, formatValue(f.comparisonValue()));
        }
        if (filter instanceof IsGreaterThan f) {
            return format("%s > %s", formatKey(f.key(), f.comparisonValue().getClass()),
                    formatValue(f.comparisonValue()));
        }
        if (filter instanceof IsGreaterThanOrEqualTo f) {
            return format("%s >= %s", formatKey(f.key(), f.comparisonValue().getClass()),
                    formatValue(f.comparisonValue()));
        }
        if (filter instanceof IsLessThan f) {
            return format("%s < %s", formatKey(f.key(), f.comparisonValue().getClass()),
                    formatValue(f.comparisonValue()));
        }
        if (filter instanceof IsLessThanOrEqualTo f) {
            return format("%s <= %s", formatKey(f.key(), f.comparisonValue().getClass()),
                    formatValue(f.comparisonValue()));
        }
        if (filter instanceof IsIn f) {
            return format("%s in %s", formatKeyAsString(f.key()), formatValues(f.comparisonValues()));
        }
        if (filter instanceof IsNotIn f) {
            String key = formatKeyAsString(f.key());
            return format("(%s is null or %s not in %s)", key, key, formatValues(f.comparisonValues()));
        }
        if (filter instanceof And a) {
            return format("(%s and %s)", map(a.left()), map(a.right()));
        }
        if (filter instanceof Or o) {
            return format("(%s or %s)", map(o.left()), map(o.right()));
        }
        if (filter instanceof Not n) {
            return format("not(%s)", map(n.expression()));
        }
        throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
    }

    private String formatKey(String key, Class<?> valueType) {
        String sqlType = SQL_TYPE_MAP.getOrDefault(valueType, "text");
        return format("(%s->>'%s')::%s", metadataColumn, sanitize(key), sqlType);
    }

    private String formatKeyAsString(String key) {
        return format("%s->>'%s'", metadataColumn, sanitize(key));
    }

    private String formatValue(Object value) {
        if (value instanceof String s) {
            return "'" + s.replace("'", "''") + "'";
        }
        if (value instanceof UUID) {
            return "'" + value + "'";
        }
        return value.toString();
    }

    /**
     * Always quote IN/NOT IN values as text because the left-hand side comes from
     * {@code metadata->>'key'} which is {@code text} in CockroachDB. A bare integer
     * literal on the right-hand side raises "unsupported comparison operator".
     */
    private String formatValues(Collection<?> values) {
        return "("
                + values.stream()
                        .map(v -> "'" + String.valueOf(v).replace("'", "''") + "'")
                        .collect(Collectors.joining(","))
                + ")";
    }

    private static String sanitize(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("filter key must not be empty");
        }
        String clean = key.trim();
        if (!clean.matches("^[a-zA-Z0-9_.\\-]+$")) {
            throw new IllegalArgumentException("Invalid filter key '" + clean
                    + "'. Only alphanumeric, underscore, dot and hyphen are allowed.");
        }
        return clean;
    }
}
