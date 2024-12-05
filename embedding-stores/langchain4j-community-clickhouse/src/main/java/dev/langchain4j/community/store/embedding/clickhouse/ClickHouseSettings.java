package dev.langchain4j.community.store.embedding.clickhouse;

import static dev.langchain4j.community.store.embedding.clickhouse.ClickHouseMappingKey.REQUIRED_COLUMN_MAP_KEYS;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.clickhouse.data.ClickHouseDataType;
import java.util.HashMap;
import java.util.Map;

public class ClickHouseSettings {

    private static final Map<String, String> DEFAULT_COLUMN_MAP = new HashMap<>();

    static {
        DEFAULT_COLUMN_MAP.put("text", "text");
        DEFAULT_COLUMN_MAP.put("id", "id");
        DEFAULT_COLUMN_MAP.put("embedding", "embedding");
    }

    private String url;
    private String username;
    private String password;
    private String database;
    private String table;
    /**
     * Column type map to project column name onto langchain4j semantics.
     * <p>Must have keys: `text`, `id`, `embedding`</p>
     */
    private Map<String, String> columnMap;
    /**
     *
     */
    private Map<String, ClickHouseDataType> metadataTypeMap;

    private Integer dimension;
    private Long timeout;

    /**
     * Construct a ClickHouseSettings instance
     *
     * @param url             ClickHouse http endpoint. (e.g. http://localhost:8123)
     * @param username        Username. (Optional)
     * @param password        Password. (Optional)
     * @param database        Database name. (Optional)
     * @param table           Table name. (Optional)
     * @param columnMap       Column type map to project column name onto langchain4j semantics.
     *                        Must have keys: `text`, `id` and `embedding`, other keys will be ignored. (Optional)
     * @param metadataTypeMap Metadata type map to project column name onto ClickHouseDataType in order to filter.
     * @param dimension       Embedding dimension.
     * @param timeout         Request timeout in milliseconds. (Optional)
     */
    public ClickHouseSettings(
            String url,
            String username,
            String password,
            String database,
            String table,
            Map<String, String> columnMap,
            Map<String, ClickHouseDataType> metadataTypeMap,
            Integer dimension,
            Long timeout) {
        this.url = ensureNotNull(url, "url");
        this.username = username;
        this.password = password;
        this.database = getOrDefault(database, "default");
        this.table = getOrDefault(table, "langchain4j_table");
        this.columnMap = getOrDefault(columnMap, DEFAULT_COLUMN_MAP);
        this.metadataTypeMap = metadataTypeMap;
        this.dimension = ensureNotNull(dimension, "dimension");
        this.timeout = getOrDefault(timeout, 3000L);

        ensureColumnMap(this.columnMap);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Map<String, String> getColumnMap() {
        return columnMap;
    }

    public Map<String, ClickHouseDataType> getMetadataTypeMap() {
        return metadataTypeMap;
    }

    public void setMetadataTypeMap(Map<String, ClickHouseDataType> metadataTypeMap) {
        this.metadataTypeMap = metadataTypeMap;
    }

    public void setColumnMap(Map<String, String> columnMap) {
        this.columnMap = columnMap;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public boolean containsMetadata() {
        return metadataTypeMap != null && !metadataTypeMap.isEmpty();
    }

    public boolean containsMetadataKey(String key) {
        return containsMetadata() && metadataTypeMap.containsKey(key);
    }

    public String getColumnMapping(String key) {
        if (!containsMetadata()) {
            return null;
        }

        return columnMap.get(key);
    }

    private static void ensureColumnMap(Map<String, String> columnMap) {
        REQUIRED_COLUMN_MAP_KEYS.forEach(requiredColumn -> {
            if (!columnMap.containsKey(requiredColumn)) {
                throw illegalArgument("ColumnMap must contains key %s", requiredColumn);
            }
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String url;
        private String username;
        private String password;
        private String database;
        private String table;
        private Map<String, String> columnMap;
        private Map<String, ClickHouseDataType> metadataTypeMap;
        private Integer dimension;
        private Long timeout;

        /**
         * ClickHouse HTTP endpoint. e.g. http://localhost:8123
         *
         * @param url ClickHouse HTTP endpoint
         * @return builder
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        /**
         * Column type map to project column name onto langchain4j semantics.
         * <p>Must have keys: `text`, `id`, `embedding`</p>
         * <p>Other keys will be ignored.</p>
         *
         * @param columnMap column map
         * @return builder
         */
        public Builder columnMap(Map<String, String> columnMap) {
            this.columnMap = columnMap;
            return this;
        }

        /**
         * Metadata type map to project column name onto ClickHouseDataType in order to filter and create as separate table column.
         *
         * @param metadataTypeMap Metadata name and type map.
         * @return builder
         */
        public Builder metadataTypeMap(Map<String, ClickHouseDataType> metadataTypeMap) {
            this.metadataTypeMap = metadataTypeMap;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder timeout(Long timeout) {
            this.timeout = timeout;
            return this;
        }

        public ClickHouseSettings build() {
            return new ClickHouseSettings(
                    url, username, password, database, table, columnMap, metadataTypeMap, dimension, timeout);
        }
    }
}
