package dev.langchain4j.community.store.filter;

/**
 * Abstract base class for filter expressions with common functionality.
 * Provides implementations for logical operators (AND, OR, NOT) to combine filters.
 */
public abstract class AbstractFilterExpression implements FilterExpression {

    /**
     * Protected default constructor.
     * This class is meant to be extended by specific filter implementations.
     */
    protected AbstractFilterExpression() {
        // Default constructor
    }

    /**
     * Combines this filter with another using logical AND.
     *
     * @param other The filter to combine with
     * @return A new filter representing the AND combination
     */
    @Override
    public FilterExpression and(FilterExpression other) {
        if (other == null) {
            return this;
        }

        if ("*".equals(this.toRedisQueryString())) {
            return other;
        }

        if ("*".equals(other.toRedisQueryString())) {
            return this;
        }

        return new AndFilterExpression(this, other);
    }

    /**
     * Combines this filter with another using logical OR.
     *
     * @param other The filter to combine with
     * @return A new filter representing the OR combination
     */
    @Override
    public FilterExpression or(FilterExpression other) {
        if (other == null) {
            return this;
        }

        if ("*".equals(this.toRedisQueryString())) {
            return other;
        }

        if ("*".equals(other.toRedisQueryString())) {
            return this;
        }

        return new OrFilterExpression(this, other);
    }

    /**
     * Creates a negation of this filter.
     *
     * @return A new filter representing the NOT operation
     */
    @Override
    public FilterExpression not() {
        if ("*".equals(this.toRedisQueryString())) {
            return this;
        }

        return new NotFilterExpression(this);
    }

    /**
     * AND filter expression that combines two filter expressions.
     */
    static class AndFilterExpression extends AbstractFilterExpression {
        private final FilterExpression left;
        private final FilterExpression right;

        /**
         * Creates a new AND filter expression that combines two filter expressions.
         *
         * @param left The left-hand side filter expression
         * @param right The right-hand side filter expression
         */
        AndFilterExpression(FilterExpression left, FilterExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public String toRedisQueryString() {
            String leftStr = left.toRedisQueryString();
            String rightStr = right.toRedisQueryString();

            if ("*".equals(leftStr) && "*".equals(rightStr)) {
                return "*";
            }

            if ("*".equals(leftStr)) {
                return rightStr;
            }

            if ("*".equals(rightStr)) {
                return leftStr;
            }

            // Format with parentheses to match test expectations
            return String.format("(%s %s)", leftStr, rightStr);
        }
    }

    /**
     * OR filter expression that combines two filter expressions.
     */
    static class OrFilterExpression extends AbstractFilterExpression {
        private final FilterExpression left;
        private final FilterExpression right;

        /**
         * Creates a new OR filter expression that combines two filter expressions.
         *
         * @param left The left-hand side filter expression
         * @param right The right-hand side filter expression
         */
        OrFilterExpression(FilterExpression left, FilterExpression right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public String toRedisQueryString() {
            String leftStr = left.toRedisQueryString();
            String rightStr = right.toRedisQueryString();

            if ("*".equals(leftStr) && "*".equals(rightStr)) {
                return "*";
            }

            if ("*".equals(leftStr)) {
                return rightStr;
            }

            if ("*".equals(rightStr)) {
                return leftStr;
            }

            return String.format("(%s | %s)", leftStr, rightStr);
        }
    }

    /**
     * NOT filter expression that negates a filter expression.
     */
    static class NotFilterExpression extends AbstractFilterExpression {
        private final FilterExpression expression;

        /**
         * Creates a new NOT filter expression that negates a filter expression.
         *
         * @param expression The filter expression to negate
         */
        NotFilterExpression(FilterExpression expression) {
            this.expression = expression;
        }

        @Override
        public String toRedisQueryString() {
            String exprStr = expression.toRedisQueryString();

            if ("*".equals(exprStr)) {
                return "*";
            }

            return String.format("(-%s)", exprStr);
        }
    }
}
