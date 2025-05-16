package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the FilterExpression interface and implementations.
 */
public class FilterExpressionTest {

    // Simple test implementation of FilterExpression that returns a fixed string
    static class TestFilterExpression extends AbstractFilterExpression {
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
    public void testAndOperator() {
        FilterExpression filter1 = new TestFilterExpression("@field1:{value1}");
        FilterExpression filter2 = new TestFilterExpression("@field2:{value2}");

        FilterExpression combined = filter1.and(filter2);
        assertEquals("(@field1:{value1} @field2:{value2})", combined.toRedisQueryString());
    }

    @Test
    public void testOrOperator() {
        FilterExpression filter1 = new TestFilterExpression("@field1:{value1}");
        FilterExpression filter2 = new TestFilterExpression("@field2:{value2}");

        FilterExpression combined = filter1.or(filter2);
        assertEquals("(@field1:{value1} | @field2:{value2})", combined.toRedisQueryString());
    }

    @Test
    public void testNotOperator() {
        FilterExpression filter = new TestFilterExpression("@field:{value}");

        FilterExpression negated = filter.not();
        assertEquals("(-@field:{value})", negated.toRedisQueryString());
    }

    @Test
    public void testComplexCombinations() {
        FilterExpression filter1 = new TestFilterExpression("@field1:{value1}");
        FilterExpression filter2 = new TestFilterExpression("@field2:{value2}");
        FilterExpression filter3 = new TestFilterExpression("@field3:{value3}");

        FilterExpression combined = filter1.and(filter2).or(filter3);
        assertEquals("((@field1:{value1} @field2:{value2}) | @field3:{value3})", combined.toRedisQueryString());

        FilterExpression combined2 = filter1.and(filter2.or(filter3));
        assertEquals("(@field1:{value1} (@field2:{value2} | @field3:{value3}))", combined2.toRedisQueryString());

        FilterExpression combined3 = filter1.and(filter2).and(filter3);
        assertEquals("((@field1:{value1} @field2:{value2}) @field3:{value3})", combined3.toRedisQueryString());

        FilterExpression combined4 = filter1.or(filter2).or(filter3);
        assertEquals("((@field1:{value1} | @field2:{value2}) | @field3:{value3})", combined4.toRedisQueryString());
    }

    @Test
    public void testNullHandling() {
        FilterExpression filter = new TestFilterExpression("@field:{value}");

        assertEquals("@field:{value}", filter.and(null).toRedisQueryString());
        assertEquals("@field:{value}", filter.or(null).toRedisQueryString());
    }

    @Test
    public void testWildcardHandling() {
        FilterExpression wildcard = new TestFilterExpression("*");
        FilterExpression filter = new TestFilterExpression("@field:{value}");

        assertEquals("@field:{value}", wildcard.and(filter).toRedisQueryString());
        assertEquals("@field:{value}", filter.and(wildcard).toRedisQueryString());

        assertEquals("@field:{value}", wildcard.or(filter).toRedisQueryString());
        assertEquals("@field:{value}", filter.or(wildcard).toRedisQueryString());

        assertEquals("*", wildcard.not().toRedisQueryString());

        // Two wildcards combined should still be wildcard
        assertEquals("*", wildcard.and(wildcard).toRedisQueryString());
        assertEquals("*", wildcard.or(wildcard).toRedisQueryString());
    }

    @Test
    public void testRedisFilterExpression() {
        FilterExpression filter = new TestFilterExpression("@field:{value}");
        RedisFilterExpression redisFilter = new RedisFilterExpression(filter);

        // The RedisFilterExpression should delegate toRedisQueryString to its wrapped expression
        assertEquals("@field:{value}", redisFilter.toRedisQueryString());

        // The test method always returns true
        assertTrue(redisFilter.test(null));
    }
}
