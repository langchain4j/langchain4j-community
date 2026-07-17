package dev.langchain4j.community.store.embedding.oceanbase.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = OceanBaseEmbeddingStoreProperties.CONFIG_PREFIX)
public class OceanBaseEmbeddingStoreProperties {

    static final String CONFIG_PREFIX = "langchain4j.community.oceanbase";

    private String url;
    private String user;
    private String password;
    private String tableName;
    private Integer dimension;
    private String metricType;
    private Boolean retrieveEmbeddingsOnSearch;
    private String idFieldName;
    private String textFieldName;
    private String metadataFieldName;
    private String vectorFieldName;
    private Boolean enableHybridSearch;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public Boolean getRetrieveEmbeddingsOnSearch() {
        return retrieveEmbeddingsOnSearch;
    }

    public void setRetrieveEmbeddingsOnSearch(Boolean retrieveEmbeddingsOnSearch) {
        this.retrieveEmbeddingsOnSearch = retrieveEmbeddingsOnSearch;
    }

    public String getIdFieldName() {
        return idFieldName;
    }

    public void setIdFieldName(String idFieldName) {
        this.idFieldName = idFieldName;
    }

    public String getTextFieldName() {
        return textFieldName;
    }

    public void setTextFieldName(String textFieldName) {
        this.textFieldName = textFieldName;
    }

    public String getMetadataFieldName() {
        return metadataFieldName;
    }

    public void setMetadataFieldName(String metadataFieldName) {
        this.metadataFieldName = metadataFieldName;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public void setVectorFieldName(String vectorFieldName) {
        this.vectorFieldName = vectorFieldName;
    }

    public Boolean getEnableHybridSearch() {
        return enableHybridSearch;
    }

    public void setEnableHybridSearch(Boolean enableHybridSearch) {
        this.enableHybridSearch = enableHybridSearch;
    }
}
