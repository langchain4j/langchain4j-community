package dev.langchain4j.community.store.memory.chat.sql;

/**
 * Interface to implement an SQL Dialect.
 */
public interface SQLDialect {

    /**
     * Makes query to create a table.
     *
     * @param table name of the table.
     * @param memoryIdColumnName name of the column with memory id.
     * @param contentColumnName name of the column with content.
     * @return query to create a table.
     */
    String createTableSql(String table, String memoryIdColumnName, String contentColumnName);

    /**
     * Makes a query to update and insert a row/rows.
     *
     * @param table name of the table.
     * @param memoryIdColumnName name of the column with memory id.
     * @param contentColumnName name of the column with content.
     * @return query to update and insert a row.
     */
    String upsertSql(String table, String memoryIdColumnName, String contentColumnName);

    /**
     * Makes a query to delete a row/rows.
     *
     * @param table name of the table.
     * @param memoryIdColumnName name of the column with memory id.
     * @return query to delete a row/rows.
     */
    String deleteSql(String table, String memoryIdColumnName);

    /**
     * Makes a query to select a row/rows.
     *
     * @param table name of the table.
     * @param memoryIdColumnName name of the column with memory id.
     * @param contentColumnName name of the column with memory id.
     * @return query to select row/rows
     */
    String selectSql(String table, String memoryIdColumnName, String contentColumnName);
}
