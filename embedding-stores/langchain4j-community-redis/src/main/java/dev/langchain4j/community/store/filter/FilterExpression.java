package dev.langchain4j.community.store.filter;

/**
 * Base interface for all Redis-specific filter expressions.
 * Can be used to create complex filter expressions for Redis.
 */
public interface FilterExpression {

    /**
     * Combines this filter with another using logical AND.
     *
     * @param other The filter to combine with
     * @return A new filter representing the AND combination
     */
    FilterExpression and(FilterExpression other);

    /**
     * Combines this filter with another using logical OR.
     *
     * @param other The filter to combine with
     * @return A new filter representing the OR combination
     */
    FilterExpression or(FilterExpression other);

    /**
     * Creates a negation of this filter.
     *
     * @return A new filter representing the NOT operation
     */
    FilterExpression not();

    /**
     * Converts this filter expression to a Redis query string.
     *
     * @return Redis query string representation of this filter
     */
    String toRedisQueryString();
}
