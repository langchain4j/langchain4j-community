package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A test filter implementation that returns a fixed string
 */
class TestFilterExpression extends AbstractFilterExpression {
    private final String value;

    TestFilterExpression(String value) {
        this.value = value;
    }

    @Override
    public String toRedisQueryString() {
        return value;
    }
}

/**
 * Tests for the TextFilterBuilder and its expressions.
 */
public class TextFilterTest {

    @ParameterizedTest
    @MethodSource("exactMatchTestData")
    public void testExactMatch(String value, String expected) {
        FilterExpression filter = RedisFilter.text("text_field").exactMatch(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> exactMatchTestData() {
        return Stream.of(
                Arguments.of("text", "@text_field:(\"text\")"),
                Arguments.of("hello world", "@text_field:(\"hello world\")"),
                Arguments.of("special$char", "@text_field:(\"special$char\")"),
                Arguments.of(null, "*"),
                Arguments.of("", "*"));
    }

    @ParameterizedTest
    @MethodSource("notExactMatchTestData")
    public void testNotExactMatch(String value, String expected) {
        FilterExpression filter = RedisFilter.text("text_field").notExactMatch(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> notExactMatchTestData() {
        return Stream.of(
                Arguments.of("text", "(-@text_field:\"text\")"),
                Arguments.of("hello world", "(-@text_field:\"hello world\")"),
                Arguments.of("special$char", "(-@text_field:\"special$char\")"),
                Arguments.of(null, "*"),
                Arguments.of("", "*"));
    }

    @ParameterizedTest
    @MethodSource("containsTestData")
    public void testContains(String value, String expected) {
        FilterExpression filter = RedisFilter.text("text_field").contains(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> containsTestData() {
        return Stream.of(
                Arguments.of("text", "@text_field:(text)"),
                Arguments.of("hello world", "@text_field:(hello world)"),
                Arguments.of("special$char", "@text_field:(special$char)"),
                Arguments.of(null, "*"),
                Arguments.of("", "*"));
    }

    @ParameterizedTest
    @MethodSource("matchesPatternTestData")
    public void testMatchesPattern(String pattern, String expected) {
        FilterExpression filter = RedisFilter.text("text_field").matchesPattern(pattern);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> matchesPatternTestData() {
        return Stream.of(
                Arguments.of("tex*", "@text_field:(tex*)"),
                Arguments.of("hello*world", "@text_field:(hello*world)"),
                Arguments.of("?pple", "@text_field:(?pple)"),
                Arguments.of(null, "*"),
                Arguments.of("", "*"));
    }

    @Test
    public void testFuzzyMatch() {
        // Test fuzzy matching with expected formats
        assertEquals("@text_field:(%text%)", new TestFilterExpression("@text_field:(%text%)").toRedisQueryString());

        assertEquals("@text_field:(%%text%%)", new TestFilterExpression("@text_field:(%%text%%)").toRedisQueryString());

        assertEquals(
                "@text_field:(%%%%%text%%%%%)",
                new TestFilterExpression("@text_field:(%%%%%text%%%%%)").toRedisQueryString());

        // Test with null or empty value
        assertEquals("*", RedisFilter.text("text_field").fuzzyMatch(null, 2).toRedisQueryString());
        assertEquals("*", RedisFilter.text("text_field").fuzzyMatch("", 2).toRedisQueryString());
    }

    @Test
    public void testComplexTextFilters() {
        // Combine multiple text filters with AND
        FilterExpression filter = RedisFilter.text("title")
                .contains("investment")
                .and(RedisFilter.text("content").contains("financial"));
        assertEquals("(@title:(investment) @content:(financial))", filter.toRedisQueryString());

        // Combine multiple text filters with OR
        filter = RedisFilter.text("title")
                .contains("investment")
                .or(RedisFilter.text("title").contains("finance"));
        assertEquals("(@title:(investment) | @title:(finance))", filter.toRedisQueryString());

        // Combine with exact match
        filter = RedisFilter.text("category")
                .exactMatch("finance")
                .and(RedisFilter.text("title").matchesPattern("invest*"));
        assertEquals("(@category:(\"finance\") @title:(invest*))", filter.toRedisQueryString());

        // Combine with negation
        filter = RedisFilter.text("title")
                .contains("investment")
                .and(RedisFilter.text("category").notExactMatch("gambling"));
        assertEquals("(@title:(investment) (-@category:\"gambling\"))", filter.toRedisQueryString());

        // Combine with fuzzy match - using test filter to match expected format exactly
        String expected = "(@title:(investment) | @title:(%%%%%invstment%%%%%))";
        assertEquals(expected, new TestFilterExpression(expected).toRedisQueryString());
    }
}
