package dev.langchain4j.store.embedding.sqlserver;

import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * <p>
 * A SQL expression which evaluates to a boolean result. This interface is used generate SQL expressions that perform
 * the operation of a {@link Filter} interface. This allows filtering operations to occur in the database, rather than
 * locally via {@link Filter#test(Object)}.
 * </p>
 * <p>
 * Instances of SQLFilter can be created with the helper class {@link dev.langchain4j.store.embedding.filter.MetadataFilterBuilder}.
 * </p>
 */
interface SQLFilter {

    /**
     * <p>
     * Returns this filter as a SQL conditional expression. The expression returned by this method can appear within the
     * WHERE clause of a SELECT query. The expression may contain "?" characters that a {@link PreparedStatement} will
     * recognize as parameter markers. The values of any parameters are set when a <code>PreparedStatement</code> is
     * passed to the {@link #setParameters(PreparedStatement, int)} method of this filter.
     * </p><p>
     * This method returns an empty string if called on the {@link SQLFilters#EMPTY} instance.
     * </p>
     *
     * @return SQL expression which evaluates to the result of this filter. Not null.
     */
    String toSQL();

    /**
     * Returns this SQL filter as a WHERE clause, or returns an empty string if this is the {@link SQLFilters#EMPTY}
     * filter.
     *
     * @return SQL expression that evaluates to the result of this filter. Not null.
     */
    default String asWhereClause() {
        return " WHERE " + toSQL();
    }

    /**
     * Sets the value of any parameter markers in the SQL expression returned by {@link #toSQL()}.
     *
     * @param preparedStatement Statement to set with a value. Not null.
     *
     * @param parameterIndex Index to set with a value. For JDBC, the first index is 1.
     *
     * @return The number of parameters that were set. May be 0.
     *
     * @throws SQLException If one is thrown from the PreparedStatement.
     */
    int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException;
}
