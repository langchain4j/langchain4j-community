package dev.langchain4j.community.store.embedding.dynamodb;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Maps a langchain4j {@link Filter} to a DynamoDB {@code SearchConditionExpression} for vector search.
 *
 * <p>DynamoDB vector search is intentionally restrictive about filtering: the search condition can
 * reference only top-level attributes declared in the vector index search schema (the {@code HASH}
 * key and {@code INLINE_FILTER} attributes), and those attributes support only the equality operator
 * ({@code =}). Consequently, only {@link IsEqualTo} comparisons and {@link And} combinations of them
 * are supported. Any other filter type results in an {@link UnsupportedOperationException}.
 *
 * @see <a href="https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_SearchVectors.html">SearchVectors</a>
 */
class DynamoDbMetadataFilterMapper {

    /**
     * The compiled DynamoDB search condition: the expression string plus the name and value
     * substitution maps it references.
     */
    static final class SearchCondition {
        final String expression;
        final Map<String, String> expressionAttributeNames;
        final Map<String, AttributeValue> expressionAttributeValues;

        SearchCondition(
                String expression,
                Map<String, String> expressionAttributeNames,
                Map<String, AttributeValue> expressionAttributeValues) {
            this.expression = expression;
            this.expressionAttributeNames = expressionAttributeNames;
            this.expressionAttributeValues = expressionAttributeValues;
        }
    }

    private DynamoDbMetadataFilterMapper() {
        // utility class
    }

    static SearchCondition map(Filter filter) {
        if (filter == null) {
            return null;
        }

        Map<String, String> names = new LinkedHashMap<>();
        Map<String, AttributeValue> values = new LinkedHashMap<>();
        StringBuilder expression = new StringBuilder();

        appendFilter(filter, expression, names, values, new int[] {0});

        return new SearchCondition(expression.toString(), names, values);
    }

    private static void appendFilter(
            Filter filter,
            StringBuilder expression,
            Map<String, String> names,
            Map<String, AttributeValue> values,
            int[] counter) {
        if (filter instanceof IsEqualTo isEqualTo) {
            appendEqual(isEqualTo, expression, names, values, counter);
        } else if (filter instanceof And and) {
            appendFilter(and.left(), expression, names, values, counter);
            expression.append(" AND ");
            appendFilter(and.right(), expression, names, values, counter);
        } else {
            throw new UnsupportedOperationException("DynamoDB vector search only supports equality (IsEqualTo) filters "
                    + "combined with AND. Unsupported filter type: "
                    + filter.getClass().getName());
        }
    }

    private static void appendEqual(
            IsEqualTo filter,
            StringBuilder expression,
            Map<String, String> names,
            Map<String, AttributeValue> values,
            int[] counter) {
        int index = counter[0]++;
        String namePlaceholder = "#k" + index;
        String valuePlaceholder = ":v" + index;

        names.put(namePlaceholder, filter.key());
        // Encode filter values with the same codec the store uses when writing metadata, so a filter
        // on a float/double (persisted as a tagged string) still matches the stored value.
        values.put(valuePlaceholder, DynamoDbAttributeCodec.encode(filter.comparisonValue()));

        expression.append(namePlaceholder).append(" = ").append(valuePlaceholder);
    }
}
