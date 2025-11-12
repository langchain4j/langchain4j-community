package dev.langchain4j.community.store.embedding.yugabytedb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract base class for mapping filters to SQL WHERE clauses in YugabyteDB.
 * <p>
 * Provides common filter mapping functionality while allowing specific implementations
 * for different metadata storage modes (COLUMN_PER_KEY, COMBINED_JSON, COMBINED_JSONB).
 */
abstract class YugabyteDBFilterMapper {

    static final Map<Class<?>, String> SQL_TYPE_MAP = Map.of(
            Integer.class, "int",
            Long.class, "bigint",
            Float.class, "float",
            Double.class, "float8",
            String.class, "text",
            UUID.class, "uuid",
            Boolean.class, "boolean",
            Object.class, "text");

    public String map(Filter filter) {
        if (filter instanceof ContainsString string) {
            return mapContains(string);
        } else if (filter instanceof IsEqualTo to3) {
            return mapEqual(to3);
        } else if (filter instanceof IsNotEqualTo to2) {
            return mapNotEqual(to2);
        } else if (filter instanceof IsGreaterThan than1) {
            return mapGreaterThan(than1);
        } else if (filter instanceof IsGreaterThanOrEqualTo to1) {
            return mapGreaterThanOrEqual(to1);
        } else if (filter instanceof IsLessThan than) {
            return mapLessThan(than);
        } else if (filter instanceof IsLessThanOrEqualTo to) {
            return mapLessThanOrEqual(to);
        } else if (filter instanceof IsIn in1) {
            return mapIn(in1);
        } else if (filter instanceof IsNotIn in) {
            return mapNotIn(in);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapContains(ContainsString containsString) {
        String key =
                formatKey(containsString.key(), containsString.comparisonValue().getClass());
        return format("%s is not null and %s ~ %s", key, key, formatValue(containsString.comparisonValue()));
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key =
                formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return format("%s is null or %s != %s", key, key, formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return format(
                "%s > %s",
                formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return format(
                "%s >= %s",
                formatKey(
                        isGreaterThanOrEqualTo.key(),
                        isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return format(
                "%s < %s",
                formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return format(
                "%s <= %s",
                formatKey(
                        isLessThanOrEqualTo.key(),
                        isLessThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private String mapIn(IsIn isIn) {
        return format("%s in %s", formatKeyAsString(isIn.key()), formatValuesAsString(isIn.comparisonValues()));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        String key = formatKeyAsString(isNotIn.key());
        return format("%s is null or %s not in %s", key, key, formatValuesAsString(isNotIn.comparisonValues()));
    }

    private String mapAnd(And and) {
        return format("%s and %s", map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return format("not(%s)", map(not.expression()));
    }

    private String mapOr(Or or) {
        return format("(%s or %s)", map(or.left()), map(or.right()));
    }

    abstract String formatKey(String key, Class<?> valueType);

    abstract String formatKeyAsString(String key);

    String formatValue(Object value) {
        if (value instanceof String stringValue) {
            final String escapedValue = stringValue.replace("'", "''");
            return "'" + escapedValue + "'";
        } else if (value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> format("'%s'", v)).collect(Collectors.joining(",")) + ")";
    }
}
