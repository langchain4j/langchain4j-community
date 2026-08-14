package dev.langchain4j.community.store.memory.chat.sql;

public class H2Dialect implements SQLDialect {

    @Override
    public String createTableSql(String table, String memoryIdColumnName, String contentColumnName) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + memoryIdColumnName + " VARCHAR(255) PRIMARY KEY, "
                + contentColumnName + " TEXT NOT NULL DEFAULT '')";
    }

    @Override
    public String upsertSql(String table, String memoryIdColumnName, String contentColumnName) {
        return "MERGE INTO " + table + " (" + memoryIdColumnName + ", " + contentColumnName + ") " + "KEY("
                + memoryIdColumnName + ") VALUES (?, ?)";
    }

    @Override
    public String deleteSql(String table, String memoryIdColumnName) {
        return "DELETE FROM " + table + " WHERE " + memoryIdColumnName + " = ?";
    }

    @Override
    public String selectSql(String table, String memoryIdColumnName, String contentColumnName) {
        return "SELECT " + contentColumnName + " FROM " + table + " WHERE " + memoryIdColumnName + " = ?";
    }
}
