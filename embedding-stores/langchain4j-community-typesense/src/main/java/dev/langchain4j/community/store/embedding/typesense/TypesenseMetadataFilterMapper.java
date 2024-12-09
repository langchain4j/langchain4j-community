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
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TypesenseMetadataFilterMapper {

    private static final Logger log = LoggerFactory.getLogger(TypesenseMetadataFilterMapper.class);

    private final String metadataFieldName;

    TypesenseMetadataFilterMapper(String metadataFieldName) {
        this.metadataFieldName = metadataFieldName;
    }

    static TypesenseMetadataFilterMapper fromMetadataFieldName(String metadataFieldName) {
        return new TypesenseMetadataFilterMapper(metadataFieldName);
    }

    String map(Filter filter) {
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

    private String mapEqual(IsEqualTo isEqualTo) {
        return "%s := %s".formatted(mapKey(isEqualTo.key()), isEqualTo.comparisonValue());
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return "%s :!= %s".formatted(mapKey(isNotEqualTo.key()), isNotEqualTo.comparisonValue());
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return "%s :> %s".formatted(mapKey(isGreaterThan.key()), isGreaterThan.comparisonValue());
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return "%s :>= %s".formatted(mapKey(isGreaterThanOrEqualTo.key()), isGreaterThanOrEqualTo.comparisonValue());
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return "%s :< %s".formatted(mapKey(isLessThan.key()), isLessThan.comparisonValue());
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return "%s :<= %s".formatted(mapKey(isLessThanOrEqualTo.key()), isLessThanOrEqualTo.comparisonValue());
    }

    private String mapIn(IsIn isIn) {
        return "%s := [ %s ]"
                .formatted(
                        mapKey(isIn.key()),
                        isIn.comparisonValues().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        return "%s :!= [ %s ]"
                .formatted(
                        mapKey(isNotIn.key()),
                        isNotIn.comparisonValues().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private String mapAnd(And and) {
        return "(%s && %s)".formatted(map(and.left()), map(and.right()));
    }

    private String mapOr(Or or) {
        return "(%s || %s)".formatted(map(or.left()), map(or.right()));
    }

    private String mapNot(Not not) {
        Filter expression = not.expression();
        if (expression instanceof IsEqualTo isEqualTo) {
            return map(new IsNotEqualTo(isEqualTo.key(), isEqualTo.comparisonValue()));
        } else if (expression instanceof IsNotEqualTo isNotEqualTo) {
            return map(new IsEqualTo(isNotEqualTo.key(), isNotEqualTo.comparisonValue()));
        } else if (expression instanceof IsGreaterThan isGreaterThan) {
            return map(new IsLessThanOrEqualTo(isGreaterThan.key(), isGreaterThan.comparisonValue()));
        } else if (expression instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return map(new IsLessThan(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue()));
        } else if (expression instanceof IsLessThan isLessThan) {
            return map(new IsGreaterThanOrEqualTo(isLessThan.key(), isLessThan.comparisonValue()));
        } else if (expression instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return map(new IsGreaterThan(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue()));
        } else if (expression instanceof IsIn isIn) {
            return map(new IsNotIn(isIn.key(), isIn.comparisonValues()));
        } else if (expression instanceof IsNotIn isNotIn) {
            return map(new IsIn(isNotIn.key(), isNotIn.comparisonValues()));
        } else {
            log.error("Typesense do not support {} not operation", not);
            throw new UnsupportedOperationException("Typesense do not support this not operation");
        }
    }

    private String mapKey(String key) {
        return "%s.%s".formatted(metadataFieldName, key);
    }
}
