package dev.langchain4j.community.clickhouse.spring;

import static dev.langchain4j.community.clickhouse.spring.ClickHouseEmbeddingStoreProperties.PREFIX;

import com.clickhouse.data.ClickHouseDataType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = PREFIX)
public class ClickHouseEmbeddingStoreProperties {

    static final String PREFIX = "langchain4j.community.clickhouse";

    private String url;
    private String username;
    private String password;
    private String database;
    private String table;
    private Map<String, String> columnMap;
    private Map<String, ClickHouseDataType> metadataTypeMap;

    private Integer dimension;
    private Long timeout;

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
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

    public void setColumnMap(Map<String, String> columnMap) {
        this.columnMap = columnMap;
    }

    public Map<String, ClickHouseDataType> getMetadataTypeMap() {
        return metadataTypeMap;
    }

    public void setMetadataTypeMap(Map<String, ClickHouseDataType> metadataTypeMap) {
        this.metadataTypeMap = metadataTypeMap;
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
}
