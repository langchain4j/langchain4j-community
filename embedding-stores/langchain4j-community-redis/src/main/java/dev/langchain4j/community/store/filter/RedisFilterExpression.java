package dev.langchain4j.community.store.filter;

import dev.langchain4j.store.embedding.filter.Filter;

/**
 * Adapter that implements the LangChain4j Filter interface while
 * providing Redis-specific filtering capabilities.
 */
public class RedisFilterExpression implements Filter {

    private final FilterExpression expression;

    /**
     * Creates a new RedisFilterExpression that adapts a Redis-specific
     * filter expression to the LangChain4j Filter interface.
     *
     * @param expression The Redis-specific filter expression
     */
    public RedisFilterExpression(FilterExpression expression) {
        this.expression = expression;
    }

    /**
     * Tests if a given object satisfies this filter. This implementation
     * always returns true, as Redis filters are applied on the server-side.
     *
     * @param object An object to test
     * @return true if the filter would match, false otherwise
     */
    @Override
    public boolean test(Object object) {
        // In-memory implementation would be complex and likely incomplete.
        // Redis filters are designed to be executed on the Redis server.
        // This is only used by LangChain4j for in-memory filtering, which
        // is not the primary use case for Redis filters.
        return true;
    }

    /**
     * Gets the Redis query string representation of this filter.
     *
     * @return Redis query string
     */
    public String toRedisQueryString() {
        return expression.toRedisQueryString();
    }
}
