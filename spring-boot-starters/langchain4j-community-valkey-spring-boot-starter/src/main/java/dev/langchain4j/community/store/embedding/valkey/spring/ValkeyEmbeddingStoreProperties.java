package dev.langchain4j.community.store.embedding.valkey.spring;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = ValkeyEmbeddingStoreProperties.CONFIG_PREFIX)
public class ValkeyEmbeddingStoreProperties {

    static final String CONFIG_PREFIX = "langchain4j.community.valkey";

    private String host;
    private Integer port;
    private String username;
    private String password;
    private Boolean useTls;
    private Integer requestTimeout;
    private String clientName;
    private String indexName;
    private String prefix;
    private Integer dimension;
    private List<String> metadataKeys;
    private Long operationTimeoutSeconds;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
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

    public Boolean getUseTls() {
        return useTls;
    }

    public void setUseTls(Boolean useTls) {
        this.useTls = useTls;
    }

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Integer requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public List<String> getMetadataKeys() {
        return metadataKeys;
    }

    public void setMetadataKeys(List<String> metadataKeys) {
        this.metadataKeys = metadataKeys;
    }

    public Long getOperationTimeoutSeconds() {
        return operationTimeoutSeconds;
    }

    public void setOperationTimeoutSeconds(Long operationTimeoutSeconds) {
        this.operationTimeoutSeconds = operationTimeoutSeconds;
    }
}
