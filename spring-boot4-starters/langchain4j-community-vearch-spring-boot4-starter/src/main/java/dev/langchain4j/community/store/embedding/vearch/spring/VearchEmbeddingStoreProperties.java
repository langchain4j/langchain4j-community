package dev.langchain4j.community.store.embedding.vearch.spring;

import dev.langchain4j.community.store.embedding.vearch.field.Field;
import dev.langchain4j.community.store.embedding.vearch.index.search.SearchIndexParam;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = VearchEmbeddingStoreProperties.CONFIG_PREFIX)
public class VearchEmbeddingStoreProperties {

    static final String CONFIG_PREFIX = "langchain4j.community.vearch";

    private String baseUrl;
    private Duration timeout;
    private Map<String, String> customHeaders;

    @NestedConfigurationProperty
    private Config config;

    private boolean normalizeEmbeddings;
    private boolean logRequests;
    private boolean logResponses;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public boolean isNormalizeEmbeddings() {
        return normalizeEmbeddings;
    }

    public void setNormalizeEmbeddings(boolean normalizeEmbeddings) {
        this.normalizeEmbeddings = normalizeEmbeddings;
    }

    public boolean isLogRequests() {
        return logRequests;
    }

    public void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests;
    }

    public boolean isLogResponses() {
        return logResponses;
    }

    public void setLogResponses(boolean logResponses) {
        this.logResponses = logResponses;
    }

    public static class Config {

        private String databaseName;
        private String spaceName;
        private Integer dimension;
        private Integer replicaNum;
        private Integer partitionNum;
        private SearchIndexParam searchIndexParam;
        private List<Field> fields;
        private String embeddingFieldName;
        private String textFieldName;
        private List<String> metadataFieldNames;

        public String getDatabaseName() {
            return databaseName;
        }

        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }

        public String getSpaceName() {
            return spaceName;
        }

        public void setSpaceName(String spaceName) {
            this.spaceName = spaceName;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public Integer getReplicaNum() {
            return replicaNum;
        }

        public void setReplicaNum(Integer replicaNum) {
            this.replicaNum = replicaNum;
        }

        public Integer getPartitionNum() {
            return partitionNum;
        }

        public void setPartitionNum(Integer partitionNum) {
            this.partitionNum = partitionNum;
        }

        public SearchIndexParam getSearchIndexParam() {
            return searchIndexParam;
        }

        public void setSearchIndexParam(SearchIndexParam searchIndexParam) {
            this.searchIndexParam = searchIndexParam;
        }

        public List<Field> getFields() {
            return fields;
        }

        public void setFields(List<Field> fields) {
            this.fields = fields;
        }

        public String getEmbeddingFieldName() {
            return embeddingFieldName;
        }

        public void setEmbeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
        }

        public String getTextFieldName() {
            return textFieldName;
        }

        public void setTextFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
        }

        public List<String> getMetadataFieldNames() {
            return metadataFieldNames;
        }

        public void setMetadataFieldNames(List<String> metadataFieldNames) {
            this.metadataFieldNames = metadataFieldNames;
        }
    }
}
