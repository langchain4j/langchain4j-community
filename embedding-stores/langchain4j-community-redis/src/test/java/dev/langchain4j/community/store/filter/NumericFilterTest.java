package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the NumericFilterBuilder and its expressions.
 */
public class NumericFilterTest {

    @ParameterizedTest
    @MethodSource("equalToTestData")
    public void testEqualTo(Number value, String expected) {
        FilterExpression filter = RedisFilter.numeric("numeric_field").equalTo(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> equalToTestData() {
        return Stream.of(
                Arguments.of(5, "@numeric_field:[5 5]"),
                Arguments.of(5.5, "@numeric_field:[5.5 5.5]"),
                Arguments.of(0, "@numeric_field:[0 0]"),
                Arguments.of(-10, "@numeric_field:[-10 -10]"),
                Arguments.of(null, "*"));
    }

    @ParameterizedTest
    @MethodSource("notEqualToTestData")
    public void testNotEqualTo(Number value, String expected) {
        FilterExpression filter = RedisFilter.numeric("numeric_field").notEqualTo(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> notEqualToTestData() {
        return Stream.of(
                Arguments.of(5, "(-@numeric_field:[5 5])"),
                Arguments.of(5.5, "(-@numeric_field:[5.5 5.5])"),
                Arguments.of(0, "(-@numeric_field:[0 0])"),
                Arguments.of(-10, "(-@numeric_field:[-10 -10])"),
                Arguments.of(null, "*"));
    }

    @ParameterizedTest
    @MethodSource("comparisonTestData")
    public void testComparisonOperators(String operator, Number value, String expected) {
        FilterExpression filter;
        switch (operator) {
            case "gt":
                filter = RedisFilter.numeric("numeric_field").greaterThan(value);
                break;
            case "ge":
                filter = RedisFilter.numeric("numeric_field").greaterThanOrEqualTo(value);
                break;
            case "lt":
                filter = RedisFilter.numeric("numeric_field").lessThan(value);
                break;
            case "le":
                filter = RedisFilter.numeric("numeric_field").lessThanOrEqualTo(value);
                break;
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> comparisonTestData() {
        return Stream.of(
                Arguments.of("gt", 5, "@numeric_field:[(5 +inf]"),
                Arguments.of("ge", 5, "@numeric_field:[5 +inf]"),
                Arguments.of("lt", 5, "@numeric_field:[-inf (5]"),
                Arguments.of("le", 5, "@numeric_field:[-inf 5]"),
                Arguments.of("gt", null, "*"),
                Arguments.of("ge", null, "*"),
                Arguments.of("lt", null, "*"),
                Arguments.of("le", null, "*"));
    }

    @ParameterizedTest
    @MethodSource("betweenTestData")
    public void testBetween(Number start, Number end, RangeType rangeType, String expected) {
        FilterExpression filter;
        if (rangeType != null) {
            filter = RedisFilter.numeric("numeric_field").between(start, end, rangeType);
        } else {
            filter = RedisFilter.numeric("numeric_field").between(start, end);
        }
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> betweenTestData() {
        return Stream.of(
                // Default (inclusive) between
                Arguments.of(2, 5, null, "@numeric_field:[2 5]"),

                // Between with explicit range types
                Arguments.of(2, 5, RangeType.INCLUSIVE, "@numeric_field:[2 5]"),
                Arguments.of(2, 5, RangeType.EXCLUSIVE, "@numeric_field:[(2 (5]"),
                Arguments.of(2, 5, RangeType.LEFT_INCLUSIVE, "@numeric_field:[2 (5]"),
                Arguments.of(2, 5, RangeType.RIGHT_INCLUSIVE, "@numeric_field:[(2 5]"),

                // Edge cases
                Arguments.of(null, 5, null, "*"),
                Arguments.of(2, null, null, "*"),
                Arguments.of(null, null, null, "*"),
                Arguments.of(-10, 10, null, "@numeric_field:[-10 10]"),
                Arguments.of(0, 0, null, "@numeric_field:[0 0]"),
                Arguments.of(5.5, 10.5, null, "@numeric_field:[5.5 10.5]"));
    }

    @Test
    public void testComplexNumericFilters() {
        // Price is greater than a value AND price is less than a value
        FilterExpression filter = RedisFilter.numeric("numeric_field")
                .greaterThanOrEqualTo(10)
                .and(RedisFilter.numeric("numeric_field").lessThanOrEqualTo(100));

        // OR combination
        FilterExpression ageFilter = RedisFilter.numeric("numeric_field")
                .lessThanOrEqualTo(18)
                .or(RedisFilter.numeric("numeric_field").greaterThan(65));

        // Between with AND
        FilterExpression yearAndRatingFilter = RedisFilter.numeric("numeric_field")
                .between(2000, 2010)
                .and(RedisFilter.numeric("numeric_field").greaterThanOrEqualTo(4.0));

        // Combination with equality
        filter = RedisFilter.numeric("numeric_field")
                .equalTo(0)
                .or(RedisFilter.numeric("numeric_field").equalTo(404));
        assertEquals("(@numeric_field:[0 0] | @numeric_field:[404 404])", filter.toRedisQueryString());
    }
}
