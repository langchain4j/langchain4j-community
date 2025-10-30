package dev.langchain4j.store.embedding.sqlserver;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.sql.Types;

/**
 * <p>
 * Factory methods for creating {@link SQLFilter} instances. The {@link #create(Filter, BiFunction)} method accepts a
 * {@link Filter} and a key mapping function. It returns an {@link SQLFilter} which performs the same logical
 * operation as the <code>Filter</code>, only using SQL expressions rather than evaluating object references in local
 * memory.
 * </p><p>
 * The key mapping function translates a {@link Metadata} key into a SQL expression which identifies the key in a
 * database. For example, if metadata keys are stored as columns of a table, then the function might return
 * <code>"table_name." + key</code>. If metadata is stored as a JSON document in a single column, then the function
 * might return <code>"JSON_VALUE(json_column, '$." + key + "')"</code>.
 * </p><p>
 * Not all {@link Filter} types are supported. If an unsupported type is passed to
 * {@link #create(Filter, BiFunction)}, then an {@link UnsupportedOperationException} is thrown.
 * </p>
 */
public class SQLFilters {

    /**
     * An SQLFilter that applies no filtering. The {@link SQLFilter#toSQL()} method of this filter returns an empty
     * string.
     */
    public static final SQLFilter EMPTY = new SQLFilter() {
        @Override
        public String toSQL() {
            return "";
        }

        @Override
        public String asWhereClause() {
            return "";
        }

        @Override
        public int setParameters(PreparedStatement preparedStatement, int parameterIndex) {
            return 0;
        }
    };

    /**
     * <p>
     * Creates a {@link SQLFilter} from a {@link Filter}. The returned <code>SQLFilter</code> will perform the same
     * logical operation as the input <code>Filter</code>, but will do so using SQL expressions rather than evaluating
     * object references in local memory.
     * </p><p>
     * The <code>keyMapper</code> function translates a {@link Metadata} key into a SQL expression which identifies the
     * key in a database. For example, if metadata keys are stored as columns of a table, then the function might
     * return <code>"table_name." + key</code>. If metadata is stored as a JSON document in a single column, then the
     * function might return <code>"JSON_VALUE(json_column, '$." + key + "')"</code>.
     * </p>
     *
     * @param filter Filter to convert into an SQLFilter. If this value is null, then the {@link #EMPTY} filter is
     *               returned.
     *
     * @param keyMapper Function that maps a metadata key into a SQL expression. Not null.
     *
     * @return SQLFilter that performs the same operation as the input Filter. Not null.
     *
     * @throws UnsupportedOperationException If the input Filter is a type that is not supported.
     */
    public static SQLFilter create(Filter filter, BiFunction<String, Integer, String> keyMapper) {
        if (filter == null) {
            return EMPTY;
        }

        if (filter instanceof final IsEqualTo isEqualTo) {
            return createComparisonFilter(isEqualTo.key(), isEqualTo.comparisonValue(), "=", keyMapper);
        } else if (filter instanceof final IsNotEqualTo isNotEqualTo) {
            return createIsNotEqualToFilter(isNotEqualTo.key(), isNotEqualTo.comparisonValue(), keyMapper);
        } else if (filter instanceof final IsGreaterThan isGreaterThan) {
            return createComparisonFilter(isGreaterThan.key(), isGreaterThan.comparisonValue(), ">", keyMapper);
        } else if (filter instanceof final IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return createComparisonFilter(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue(), ">=", keyMapper);
        } else if (filter instanceof final IsLessThan isLessThan) {
            return createComparisonFilter(isLessThan.key(), isLessThan.comparisonValue(), "<", keyMapper);
        } else if (filter instanceof final IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return createComparisonFilter(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue(), "<=", keyMapper);
        } else if (filter instanceof final IsIn isIn) {
            return createInFilter(isIn.key(), isIn.comparisonValues(), keyMapper);
        } else if (filter instanceof final IsNotIn isNotIn) {
            return createNotInFilter(isNotIn.key(), isNotIn.comparisonValues(), keyMapper);
        } else if (filter instanceof final And and) {
            return createLogicalFilter(and.left(), and.right(), "AND", keyMapper);
        } else if (filter instanceof final Or or) {
            return createLogicalFilter(or.left(), or.right(), "OR", keyMapper);
        } else if (filter instanceof final Not not) {
            SQLFilter expression = create(not.expression(), keyMapper);
            return createNotFilter(expression);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getSimpleName());
        }
    }

    /**
     * Returns the SQL Server SQL type corresponding to a Java class.
     *
     * @param javaClass The Java class.
     * @return The corresponding SQL Server SQL type.
     */
    public static int toSqlServerType(Class<?> javaClass) {
        if (Objects.equals(javaClass, String.class)) {
            return Types.NVARCHAR;
        } else if (Objects.equals(javaClass, Integer.class) || Objects.equals(javaClass, int.class)) {
            return Types.INTEGER;
        } else if (Objects.equals(javaClass, Long.class) || Objects.equals(javaClass, long.class)) {
            return Types.BIGINT;
        } else if (Objects.equals(javaClass, Double.class) || Objects.equals(javaClass, double.class)) {
            return Types.DOUBLE;
        } else if (Objects.equals(javaClass, Float.class) || Objects.equals(javaClass, float.class)) {
            return Types.REAL;
        } else if (Objects.equals(javaClass, Boolean.class) || Objects.equals(javaClass, boolean.class)) {
            return Types.BOOLEAN;
        } else {
            return Types.NVARCHAR; // Default to NVARCHAR
        }
    }

    private static SQLFilter createComparisonFilter(String key, Object value, String operator, BiFunction<String, Integer, String> keyMapper) {
        return new SQLFilter() {
            @Override
            public String toSQL() {
                Class<?> valueClass = value != null ? value.getClass() : String.class;
                int sqlServerType = toSqlServerType(valueClass);
                final String columnExpression = keyMapper.apply(key, sqlServerType);
                return columnExpression + " IS NOT NULL AND " + columnExpression + ' ' + operator + " ? ";
            }

            @Override
            public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
                preparedStatement.setObject(parameterIndex, value);
                return 1;
            }
        };
    }

    private static SQLFilter createIsNotEqualToFilter(String key, Object value, BiFunction<String, Integer, String> keyMapper) {
        return new SQLFilter() {
            @Override
            public String toSQL() {
                Class<?> valueClass = value != null ? value.getClass() : String.class;
                int sqlServerType = toSqlServerType(valueClass);
                String columnExpression = keyMapper.apply(key, sqlServerType);
                // IsNotEqualTo should be true if the key is null or the value is not the given value
                return '(' + columnExpression + " IS NULL OR " + columnExpression + " <> ?)";
            }

            @Override
            public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
                preparedStatement.setObject(parameterIndex, value);
                return 1;
            }
        };
    }

    private static SQLFilter createInFilter(String key, Collection<?> values, BiFunction<String, Integer, String> keyMapper) {
        return new SQLFilter() {
            @Override
            public String toSQL() {
                if (values.isEmpty()) {
                    return "1=0"; // Always false
                }
                Class<?> valueClass = values.iterator().next().getClass();
                int sqlServerType = toSqlServerType(valueClass);
                String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(","));
                final String columnExpression = keyMapper.apply(key, sqlServerType);
                return columnExpression + " IS NOT NULL AND " + columnExpression + " IN (" + placeholders + ')';
            }

            @Override
            public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
                int index = parameterIndex;
                for (Object value : values) {
                    preparedStatement.setObject(index++, value);
                }
                return values.size();
            }
        };
    }

    private static SQLFilter createNotInFilter(String key, Collection<?> values, BiFunction<String, Integer, String> keyMapper) {
        return new SQLFilter() {
            @Override
            public String toSQL() {
                if (values.isEmpty()) {
                    return "1=1"; // Always true
                }
                Class<?> valueClass = values.iterator().next().getClass();
                int sqlServerType = toSqlServerType(valueClass);
                String columnExpression = keyMapper.apply(key, sqlServerType);
                String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(","));
                // IsNotIn should be true if the key is null or the value is not in the given values
                return '(' + columnExpression + " IS NULL OR " + columnExpression + " NOT IN (" + placeholders + "))";
            }

            @Override
            public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
                int index = parameterIndex;
                for (Object value : values) {
                    preparedStatement.setObject(index++, value);
                }
                return values.size();
            }
        };
    }

    private static SQLFilter createLogicalFilter(Filter left, Filter right, String operator, BiFunction<String, Integer, String> keyMapper) {
        SQLFilter leftFilter = create(left, keyMapper);
        SQLFilter rightFilter = create(right, keyMapper);

        return new SQLFilter() {
            @Override
            public String toSQL() {
                return '(' + leftFilter.toSQL() + ' ' + operator + ' ' + rightFilter.toSQL() + ')';
            }

            @Override
            public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
                int leftParams = leftFilter.setParameters(preparedStatement, parameterIndex);
                int rightParams = rightFilter.setParameters(preparedStatement, parameterIndex + leftParams);
                return leftParams + rightParams;
            }
        };
    }

    private static SQLFilter createNotFilter(SQLFilter expression) {
        return new SQLFilter() {
            @Override
            public String toSQL() {
                return "NOT (" + expression.toSQL() + ')';
            }

            @Override
            public int setParameters(PreparedStatement preparedStatement, int parameterIndex) throws SQLException {
                return expression.setParameters(preparedStatement, parameterIndex);
            }
        };
    }
}
