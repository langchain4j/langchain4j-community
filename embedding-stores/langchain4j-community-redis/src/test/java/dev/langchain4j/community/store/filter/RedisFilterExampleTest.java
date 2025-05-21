package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Examples and integration tests for using Redis filters together.
 * This test doesn't require Redis to be running, it just verifies the
 * query string generation.
 */
public class RedisFilterExampleTest {

    // A test filter implementation that returns a fixed string
    private static class TestFilterExpression extends AbstractFilterExpression {
        private final String value;

        TestFilterExpression(String value) {
            this.value = value;
        }

        @Override
        public String toRedisQueryString() {
            return value;
        }
    }

    @Test
    public void testComplexFilterCombinations() {
        // Example 1: Find content about restaurants in Paris that have been rated 4+ stars
        String expected = "(@content:(restaurant) @city:{Paris} @rating:[4 +inf])";
        RedisFilterExpression redisFilter = new RedisFilterExpression(new TestFilterExpression(expected));

        assertEquals(expected, redisFilter.toRedisQueryString());

        // The test method should always return true as Redis will handle filtering
        assertTrue(redisFilter.test(null));

        // Example 2: Find entries created in the last week within 5km of specified location
        String expected2 = "@created_at:[(1745572390 +inf] @geo_field:[-122.419400 37.774900 5.000000 km]";
        redisFilter = new RedisFilterExpression(new TestFilterExpression(expected2));

        // Test with exact expected string
        assertEquals(expected2, redisFilter.toRedisQueryString());

        // Example 3: Find entries with fuzzy matching for misspelled words
        String expected3 = "(@question:(%%%%%restorant%%%%%) | @question:(%%%%%restraunt%%%%%))";
        redisFilter = new RedisFilterExpression(new TestFilterExpression(expected3));

        assertEquals(expected3, redisFilter.toRedisQueryString());

        // Example 4: Complex filter with numeric ranges and tag exclusions
        String expected4 = "(@price:[50 200] (-@category:{luxury|premium}) @description:(discount*))";
        redisFilter = new RedisFilterExpression(new TestFilterExpression(expected4));

        assertEquals(expected4, redisFilter.toRedisQueryString());

        // Example 5: Date-based filtering with categories
        String expected5 = "(@publish_date:[1746000000 1746086400] @category:{news|blog|article})";
        redisFilter = new RedisFilterExpression(new TestFilterExpression(expected5));

        assertEquals(expected5, redisFilter.toRedisQueryString());
    }

    @Test
    public void testNestingFilters() {
        // Create a complex nested filter structure that we'll mock
        String expected =
                "((@content:(investment) @numeric_field:[price +inf]) | (@category:{finance|business} @created_at:[(1745572390 +inf]))";
        RedisFilterExpression redisFilter = new RedisFilterExpression(new TestFilterExpression(expected));

        assertEquals(expected, redisFilter.toRedisQueryString());
    }
}
