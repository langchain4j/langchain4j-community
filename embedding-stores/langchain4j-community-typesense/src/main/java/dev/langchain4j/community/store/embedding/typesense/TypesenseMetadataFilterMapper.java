package dev.langchain4j.community.store.embedding.typesense;

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
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Arrays;
import java.util.Collection;

class TypesenseMetadataFilterMapper {

    private final TypesenseSchema schema;

    TypesenseMetadataFilterMapper(TypesenseSchema schema) {
        this.schema = schema;
    }

    String mapToFilter(Filter filter) {
        if (filter == null) {
            return null;
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
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo filter) {
        return String.format("%s:=%s", toKey(filter.key()), toSingleValue(filter.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo filter) {
        return String.format("%s:!=%s", toKey(filter.key()), toSingleValue(filter.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan filter) {
        return String.format("%s:>%s", toKey(filter.key()), filter.comparisonValue());
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return String.format("%s:>=%s", toKey(filter.key()), filter.comparisonValue());
    }

    private String mapLessThan(IsLessThan filter) {
        return String.format("%s:<%s", toKey(filter.key()), filter.comparisonValue());
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return String.format("%s:<=%s", toKey(filter.key()), filter.comparisonValue());
    }

    private String mapIn(IsIn filter) {
        return String.format("%s:=%s", toKey(filter.key()), toSingleValue(filter.comparisonValues()));
    }

    private String mapNotIn(IsNotIn filter) {
        return String.format("%s:!=%s", toKey(filter.key()), toSingleValue(filter.comparisonValues()));
    }

    private String mapAnd(And filter) {
        return "(" + mapToFilter(filter.left()) + " && " + mapToFilter(filter.right()) + ")";
    }

    private String mapOr(Or filter) {
        return "(" + mapToFilter(filter.left()) + " || " + mapToFilter(filter.right()) + ")";
    }

    private String toKey(String rawKey) {
        return String.format("%s.%s", schema.getMetadataFieldName(), rawKey);
    }

    private String toSingleValue(Object value) {
        if (value.getClass().isArray()) {
            String[] strings =
                    Arrays.stream(((Object[]) value)).map(String::valueOf).toArray(String[]::new);
            return String.join(", ", strings);
        }
        if (value instanceof Collection<?> collection) {
            return String.join(", ", collection.stream().map(String::valueOf).toArray(String[]::new));
        }

        return value.toString();
    }
}
