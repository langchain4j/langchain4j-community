package dev.langchain4j.community.store.embedding.valkey;

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
import java.util.stream.Collectors;

/**
 * Metadata Filter Mapper for Valkey Search.
 *
 * <p>Translates LangChain4j {@link Filter} DSL objects into Valkey Search query filter syntax.
 * The filter syntax is compatible with Valkey Search's query language.
 *
 * @see <a href="https://valkey.io/docs/topics/query/">Valkey Search Documentation</a>
 */
class ValkeyMetadataFilterMapper {

    private static final String FILTER_PREFIX = "@";
    private static final String NOT_PREFIX = "-";
    private static final String OR_DELIMITER = " | ";

    private final Map<String, FieldType> schemaFieldMap;

    ValkeyMetadataFilterMapper(Map<String, FieldType> schemaFieldMap) {
        this.schemaFieldMap = schemaFieldMap;
    }

    /**
     * Escapes special characters in tag field values to prevent query injection.
     * Valkey Search tag syntax uses {@code {, }, |, ,} as delimiters.
     */
    private static String escapeTagValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("|", "\\|")
                .replace(",", "\\,");
    }

    /**
     * Escapes special characters in text field values to prevent query injection.
     * Valkey Search text syntax uses {@code "} as a delimiter.
     */
    private static String escapeTextValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Maps a {@link Filter} to a Valkey Search filter expression string.
     *
     * @param filter the filter to map, or {@code null} for a wildcard match
     * @return the Valkey Search filter expression
     */
    String mapToFilter(Filter filter) {
        if (filter == null) {
            return "(*)";
        }

        if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(isEqualTo);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(isNotEqualTo);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(isGreaterThan);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualTo);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(isLessThan);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(isLessThanOrEqualTo);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(isIn);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(isNotIn);
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

    /**
     * Maps an equality filter.
     *
     * <ul>
     *     <li>Numeric: {@code @key:[value value]}</li>
     *     <li>Tag: {@code @key:{value}}</li>
     *     <li>Text: {@code @key:"value"}</li>
     * </ul>
     */
    String mapEqual(IsEqualTo filter) {
        return doMapEqual(filter.key(), filter.comparisonValue());
    }

    /**
     * Maps a negated equality filter.
     *
     * <ul>
     *     <li>Numeric: {@code (-@key:[value value])}</li>
     *     <li>Tag: {@code (-@key:{value})}</li>
     *     <li>Text: {@code (-@key:"value")}</li>
     * </ul>
     */
    String mapNotEqual(IsNotEqualTo filter) {
        return doMapNot(doMapEqual(filter.key(), filter.comparisonValue()));
    }

    /**
     * Maps a greater-than filter. Only supports numeric fields.
     *
     * <p>Numeric: {@code @key:[(value inf]}</p>
     */
    String mapGreaterThan(IsGreaterThan filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), true);
        return doMapCompare(filter.key(), value.toString(), Numeric.POSITIVE_INFINITY.toString());
    }

    /**
     * Maps a greater-than-or-equal filter. Only supports numeric fields.
     *
     * <p>Numeric: {@code @key:[value inf]}</p>
     */
    String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), false);
        return doMapCompare(filter.key(), value.toString(), Numeric.POSITIVE_INFINITY.toString());
    }

    /**
     * Maps a less-than filter. Only supports numeric fields.
     *
     * <p>Numeric: {@code @key:[-inf (value]}</p>
     */
    String mapLessThan(IsLessThan filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), true);
        return doMapCompare(filter.key(), Numeric.NEGATIVE_INFINITY.toString(), value.toString());
    }

    /**
     * Maps a less-than-or-equal filter. Only supports numeric fields.
     *
     * <p>Numeric: {@code @key:[-inf value]}</p>
     */
    String mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        Numeric value = Numeric.constructNumeric(filter.comparisonValue(), false);
        return doMapCompare(filter.key(), Numeric.NEGATIVE_INFINITY.toString(), value.toString());
    }

    /**
     * Maps an in-set filter. Only supports tag and text fields.
     *
     * <ul>
     *     <li>Tag: {@code @key:{value1 | value2 | ...}}</li>
     *     <li>Text: {@code @key:("value1" | "value2" | ...)}</li>
     * </ul>
     */
    String mapIn(IsIn filter) {
        return doMapIn(filter.key(), filter.comparisonValues());
    }

    /**
     * Maps a negated in-set filter. Only supports tag and text fields.
     * For multiple values, generates individual negations combined with AND (implicit conjunction)
     * to avoid parsing issues with OR operators inside negated expressions in Valkey Search.
     *
     * <ul>
     *     <li>Tag single: {@code (-@key:{value})}</li>
     *     <li>Tag multi: {@code (-@key:{value1}) (-@key:{value2})}</li>
     *     <li>Text single: {@code (-@key:"value")}</li>
     *     <li>Text multi: {@code (-@key:"value1") (-@key:"value2")}</li>
     * </ul>
     */
    String mapNotIn(IsNotIn filter) {
        FieldType fieldType = schemaFieldMap.getOrDefault(filter.key(), FieldType.TAG);
        Collection<?> values = filter.comparisonValues();

        if (values.size() == 1) {
            return doMapNot(doMapIn(filter.key(), values));
        }

        // For multiple values, generate individual negations to avoid Valkey parsing issues
        // with OR operators inside negated expressions
        String keyPrefix = toKeyPrefix(filter.key());
        return switch (fieldType) {
            case TAG ->
                values.stream()
                        .map(v -> doMapNot(keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(v)))
                        .collect(Collectors.joining(" "));
            case TEXT ->
                values.stream()
                        .map(v -> doMapNot(keyPrefix + Boundary.TEXT_BOUNDARY.toSingleString(v)))
                        .collect(Collectors.joining(" "));
            case NUMERIC ->
                throw new UnsupportedOperationException(
                        "Valkey does not support numeric \"not in\" search, fieldType: " + fieldType);
        };
    }

    /**
     * Maps a logical AND filter: {@code (left right)}.
     */
    String mapAnd(And filter) {
        return "(" + mapToFilter(filter.left()) + " " + mapToFilter(filter.right()) + ")";
    }

    /**
     * Maps a logical NOT filter: {@code (-filter)}.
     */
    String mapNot(Not filter) {
        return doMapNot(mapToFilter(filter.expression()));
    }

    /**
     * Maps a logical OR filter: {@code (left | right)}.
     */
    String mapOr(Or filter) {
        return "(" + mapToFilter(filter.left()) + OR_DELIMITER + mapToFilter(filter.right()) + ")";
    }

    private String doMapEqual(String key, Object value) {
        FieldType fieldType = schemaFieldMap.getOrDefault(key, FieldType.TAG);

        String keyPrefix = toKeyPrefix(key);
        return switch (fieldType) {
            case NUMERIC -> keyPrefix + Boundary.NUMERIC_BOUNDARY.toRangeString(value, value);
            case TAG -> keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(value);
            case TEXT -> keyPrefix + Boundary.TEXT_BOUNDARY.toSingleString(value);
        };
    }

    private String doMapCompare(String key, String leftValue, String rightValue) {
        FieldType fieldType = schemaFieldMap.getOrDefault(key, FieldType.TAG);

        if (fieldType == FieldType.NUMERIC) {
            return toKeyPrefix(key) + Boundary.NUMERIC_BOUNDARY.toRangeString(leftValue, rightValue);
        } else {
            throw new UnsupportedOperationException(
                    "Valkey does not support non-numeric range search, fieldType: " + fieldType);
        }
    }

    private String doMapIn(String key, Collection<?> values) {
        FieldType fieldType = schemaFieldMap.getOrDefault(key, FieldType.TAG);

        String keyPrefix = toKeyPrefix(key);
        return switch (fieldType) {
            case TAG -> {
                if (values.size() == 1) {
                    String val = values.iterator().next().toString();
                    yield keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(val);
                }
                // For multiple values, use separate tag matches combined with OR
                // to avoid parsing issues with | inside tag braces in Valkey
                String orFilter = values.stream()
                        .map(v -> keyPrefix + Boundary.TAG_BOUNDARY.toSingleString(v))
                        .collect(Collectors.joining(OR_DELIMITER));
                yield "(" + orFilter + ")";
            }
            case TEXT -> {
                String inFilter = values.stream()
                        .map(Boundary.TEXT_BOUNDARY::toSingleString)
                        .collect(Collectors.joining(OR_DELIMITER));
                yield keyPrefix + Boundary.TEXT_IN_BOUNDARY.toSingleString(inFilter);
            }
            case NUMERIC ->
                throw new UnsupportedOperationException(
                        "Valkey does not support numeric \"in\" search, fieldType: " + fieldType);
        };
    }

    private String doMapNot(String filter) {
        return String.format("(%s%s)", NOT_PREFIX, filter);
    }

    private String toKeyPrefix(String key) {
        if (!key.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Metadata key contains unsafe characters: " + key);
        }
        return FILTER_PREFIX + key + ":";
    }

    /**
     * Represents the type of a metadata field in the Valkey index schema.
     */
    enum FieldType {
        /**
         * Numeric field type for range queries.
         */
        NUMERIC,
        /**
         * Tag field type for exact match and set membership queries.
         */
        TAG,
        /**
         * Text field type for full-text search queries.
         */
        TEXT
    }

    /**
     * Represents a numeric value in a Valkey Search range expression, with support for
     * exclusive boundaries and infinity values.
     */
    static class Numeric {

        static final Numeric POSITIVE_INFINITY = new Numeric(Double.POSITIVE_INFINITY, true);
        static final Numeric NEGATIVE_INFINITY = new Numeric(Double.NEGATIVE_INFINITY, true);

        private static final String INFINITY = "inf";
        private static final String MINUS_INFINITY = "-inf";
        private static final String INCLUSIVE_FORMAT = "%s";
        private static final String EXCLUSIVE_FORMAT = "(%s";

        private final Object value;
        private final boolean exclusive;

        Numeric(Object value, boolean exclusive) {
            this.value = value;
            this.exclusive = exclusive;
        }

        static Numeric constructNumeric(Object value, boolean exclusive) {
            return new Numeric(value, exclusive);
        }

        @Override
        public String toString() {
            if (this == POSITIVE_INFINITY) {
                return INFINITY;
            } else if (this == NEGATIVE_INFINITY) {
                return MINUS_INFINITY;
            }

            return String.format(formatString(), value);
        }

        private String formatString() {
            if (exclusive) {
                return EXCLUSIVE_FORMAT;
            }
            return INCLUSIVE_FORMAT;
        }
    }

    /**
     * Represents boundary delimiters for different Valkey Search field types.
     * Used to construct filter expressions with the correct syntax for each field type.
     */
    static class Boundary {

        static final Boundary TAG_BOUNDARY = new Boundary("{", "}");
        static final Boundary TEXT_BOUNDARY = new Boundary("\"", "\"");
        static final Boundary TEXT_IN_BOUNDARY = new Boundary("(", ")");
        static final Boundary NUMERIC_BOUNDARY = new Boundary("[", "]");

        private final String left;
        private final String right;

        Boundary(String left, String right) {
            this.left = left;
            this.right = right;
        }

        String toSingleString(Object value) {
            String escaped = escapeValue(value);
            return String.format("%s%s%s", left, escaped, right);
        }

        String toRangeString(Object leftValue, Object rightValue) {
            return String.format("%s%s %s%s", left, leftValue, rightValue, right);
        }

        private String escapeValue(Object value) {
            if (value == null) {
                return "";
            }
            String strValue = value.toString();
            if (this == TAG_BOUNDARY) {
                return escapeTagValue(strValue);
            } else if (this == TEXT_BOUNDARY) {
                return escapeTextValue(strValue);
            }
            return strValue;
        }
    }
}
