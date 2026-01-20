package dev.langchain4j.community.store.embedding.s3;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.document.Document;

/**
 * Maps langchain4j Filter to AWS S3 Vectors filter format.
 * S3 Vectors uses MongoDB-style filter syntax.
 */
class S3VectorsMetadataFilterMapper {

    private S3VectorsMetadataFilterMapper() {
        // utility class
    }

    static Document map(Filter filter) {
        if (filter == null) {
            return null;
        } else if (filter instanceof IsEqualTo isEqualTo) {
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
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Document mapEqual(IsEqualTo filter) {
        return createComparisonDocument(filter.key(), "$eq", filter.comparisonValue());
    }

    private static Document mapNotEqual(IsNotEqualTo filter) {
        return createComparisonDocument(filter.key(), "$ne", filter.comparisonValue());
    }

    private static Document mapGreaterThan(IsGreaterThan filter) {
        return createComparisonDocument(filter.key(), "$gt", filter.comparisonValue());
    }

    private static Document mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return createComparisonDocument(filter.key(), "$gte", filter.comparisonValue());
    }

    private static Document mapLessThan(IsLessThan filter) {
        return createComparisonDocument(filter.key(), "$lt", filter.comparisonValue());
    }

    private static Document mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return createComparisonDocument(filter.key(), "$lte", filter.comparisonValue());
    }

    private static Document mapIn(IsIn filter) {
        return createCollectionComparisonDocument(filter.key(), "$in", filter.comparisonValues());
    }

    private static Document mapNotIn(IsNotIn filter) {
        return createCollectionComparisonDocument(filter.key(), "$nin", filter.comparisonValues());
    }

    private static Document mapAnd(And and) {
        return createLogicalDocument("$and", map(and.left()), map(and.right()));
    }

    private static Document mapOr(Or or) {
        return createLogicalDocument("$or", map(or.left()), map(or.right()));
    }

    /**
     * S3 Vectors does not support "$not" operation directly, so we convert to inverse operations.
     */
    private static Document mapNot(Not not) {
        Filter expression = not.expression();
        if (expression instanceof IsEqualTo eq) {
            expression = new IsNotEqualTo(eq.key(), eq.comparisonValue());
        } else if (expression instanceof IsNotEqualTo neq) {
            expression = new IsEqualTo(neq.key(), neq.comparisonValue());
        } else if (expression instanceof IsGreaterThan gt) {
            expression = new IsLessThanOrEqualTo(gt.key(), gt.comparisonValue());
        } else if (expression instanceof IsGreaterThanOrEqualTo gte) {
            expression = new IsLessThan(gte.key(), gte.comparisonValue());
        } else if (expression instanceof IsLessThan lt) {
            expression = new IsGreaterThanOrEqualTo(lt.key(), lt.comparisonValue());
        } else if (expression instanceof IsLessThanOrEqualTo lte) {
            expression = new IsGreaterThan(lte.key(), lte.comparisonValue());
        } else if (expression instanceof IsIn in) {
            expression = new IsNotIn(in.key(), in.comparisonValues());
        } else if (expression instanceof IsNotIn notIn) {
            expression = new IsIn(notIn.key(), notIn.comparisonValues());
        } else if (expression instanceof And andExpr) {
            expression = new Or(Filter.not(andExpr.left()), Filter.not(andExpr.right()));
        } else if (expression instanceof Or orExpr) {
            expression = new And(Filter.not(orExpr.left()), Filter.not(orExpr.right()));
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + expression.getClass().getName());
        }
        return map(expression);
    }

    private static Document createComparisonDocument(String key, String operator, Object value) {
        Map<String, Document> operatorMap = new LinkedHashMap<>();
        operatorMap.put(operator, toDocument(value));

        Map<String, Document> result = new LinkedHashMap<>();
        result.put(key, Document.fromMap(operatorMap));

        return Document.fromMap(result);
    }

    private static Document createCollectionComparisonDocument(String key, String operator, Collection<?> values) {
        List<Document> documentList = new ArrayList<>();
        for (Object value : values) {
            documentList.add(toDocument(value));
        }

        Map<String, Document> operatorMap = new LinkedHashMap<>();
        operatorMap.put(operator, Document.fromList(documentList));

        Map<String, Document> result = new LinkedHashMap<>();
        result.put(key, Document.fromMap(operatorMap));

        return Document.fromMap(result);
    }

    private static Document createLogicalDocument(String operator, Document left, Document right) {
        List<Document> operands = new ArrayList<>();
        operands.add(left);
        operands.add(right);

        Map<String, Document> result = new LinkedHashMap<>();
        result.put(operator, Document.fromList(operands));

        return Document.fromMap(result);
    }

    private static Document toDocument(Object value) {
        if (value instanceof String) {
            return Document.fromString((String) value);
        } else if (value instanceof Number) {
            return Document.fromNumber(((Number) value).toString());
        } else if (value instanceof Boolean) {
            return Document.fromBoolean((Boolean) value);
        } else if (value instanceof UUID) {
            return Document.fromString(value.toString());
        } else {
            return Document.fromString(String.valueOf(value));
        }
    }
}
