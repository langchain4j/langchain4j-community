package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the TagFilterBuilder and its expressions.
 */
public class TagFilterTest {

    @ParameterizedTest
    @MethodSource("equalToTestData")
    public void testEqualTo(String value, String expected) {
        FilterExpression filter = RedisFilter.tag("tag_field").equalTo(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> equalToTestData() {
        return Stream.of(
                Arguments.of("simpletag", "@tag_field:{simpletag}"),
                Arguments.of("tag with space", "@tag_field:{tag\\ with\\ space}"),
                Arguments.of("special$char", "@tag_field:{special\\$char}"),
                Arguments.of(null, "*"),
                Arguments.of("", "*"));
    }

    @ParameterizedTest
    @MethodSource("notEqualToTestData")
    public void testNotEqualTo(String value, String expected) {
        FilterExpression filter = RedisFilter.tag("tag_field").notEqualTo(value);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> notEqualToTestData() {
        return Stream.of(
                Arguments.of("simpletag", "(-@tag_field:{simpletag})"),
                Arguments.of("tag with space", "(-@tag_field:{tag\\ with\\ space})"),
                Arguments.of("special$char", "(-@tag_field:{special\\$char})"),
                Arguments.of(null, "*"),
                Arguments.of("", "*"));
    }

    @ParameterizedTest
    @MethodSource("inTestData")
    public void testIn(List<String> values, String expected) {
        FilterExpression filter = RedisFilter.tag("tag_field").in(values);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> inTestData() {
        return Stream.of(
                Arguments.of(Arrays.asList("tag1", "tag2"), "@tag_field:{tag1|tag2}"),
                Arguments.of(
                        Arrays.asList("alpha", "beta with space", "gamma$special"),
                        "@tag_field:{alpha|beta\\ with\\ space|gamma\\$special}"),
                Arguments.of(Collections.emptyList(), "*"),
                Arguments.of(null, "*"),
                Arguments.of(Arrays.asList(null, "tag"), "@tag_field:{tag}"),
                Arguments.of(Arrays.asList("", "tag"), "@tag_field:{tag}"));
    }

    @Test
    public void testInVarargs() {
        FilterExpression filter = RedisFilter.tag("tag_field").in("tag1", "tag2");
        assertEquals("@tag_field:{tag1|tag2}", filter.toRedisQueryString());
    }

    @ParameterizedTest
    @MethodSource("notInTestData")
    public void testNotIn(List<String> values, String expected) {
        FilterExpression filter = RedisFilter.tag("tag_field").notIn(values);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> notInTestData() {
        return Stream.of(
                Arguments.of(Arrays.asList("tag1", "tag2"), "(-@tag_field:{tag1|tag2})"),
                Arguments.of(
                        Arrays.asList("alpha", "beta with space", "gamma$special"),
                        "(-@tag_field:{alpha|beta\\ with\\ space|gamma\\$special})"),
                Arguments.of(Collections.emptyList(), "*"),
                Arguments.of(null, "*"),
                Arguments.of(Arrays.asList(null, "tag"), "(-@tag_field:{tag})"),
                Arguments.of(Arrays.asList("", "tag"), "(-@tag_field:{tag})"));
    }

    @Test
    public void testNotInVarargs() {
        FilterExpression filter = RedisFilter.tag("tag_field").notIn("tag1", "tag2");
        assertEquals("(-@tag_field:{tag1|tag2})", filter.toRedisQueryString());
    }

    @Test
    public void testComplexTagFilters() {
        // Combine multiple tag filters with AND
        FilterExpression filter = RedisFilter.tag("category")
                .equalTo("finance")
                .and(RedisFilter.tag("status").equalTo("active"));
        assertEquals("(@category:{finance} @status:{active})", filter.toRedisQueryString());

        // Combine multiple tag filters with OR
        filter = RedisFilter.tag("category")
                .equalTo("finance")
                .or(RedisFilter.tag("category").equalTo("technology"));
        assertEquals("(@category:{finance} | @category:{technology})", filter.toRedisQueryString());

        // Combine tag filters with NOT
        filter = RedisFilter.tag("category")
                .equalTo("finance")
                .and(RedisFilter.tag("region").notEqualTo("europe"));
        assertEquals("(@category:{finance} (-@region:{europe}))", filter.toRedisQueryString());

        // Combine tag filters with IN
        filter = RedisFilter.tag("category")
                .equalTo("finance")
                .and(RedisFilter.tag("region").in("us", "asia"));
        assertEquals("(@category:{finance} @region:{us|asia})", filter.toRedisQueryString());
    }

    @Test
    public void testEscapingSpecialCharacters() {
        // Test escaping various special characters
        assertEquals(
                "@tag_field:{tag\\/with\\/slashes}",
                RedisFilter.tag("tag_field").equalTo("tag/with/slashes").toRedisQueryString());

        assertEquals(
                "@tag_field:{hypen\\-tag}",
                RedisFilter.tag("tag_field").equalTo("hypen-tag").toRedisQueryString());

        assertEquals(
                "@tag_field:{dot\\.tag}",
                RedisFilter.tag("tag_field").equalTo("dot.tag").toRedisQueryString());

        assertEquals(
                "@tag_field:{tag\\:with\\:colons}",
                RedisFilter.tag("tag_field").equalTo("tag:with:colons").toRedisQueryString());

        assertEquals(
                "@tag_field:{tag\\&another}",
                RedisFilter.tag("tag_field").equalTo("tag&another").toRedisQueryString());
    }
}
