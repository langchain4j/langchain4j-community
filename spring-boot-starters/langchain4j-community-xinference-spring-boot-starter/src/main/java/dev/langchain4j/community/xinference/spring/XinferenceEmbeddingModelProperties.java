package dev.langchain4j.community.xinference.spring;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = XinferenceEmbeddingModelProperties.PREFIX)
public class XinferenceEmbeddingModelProperties {
    static final String PREFIX = "langchain4j.community.xinference.embedding-model";

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private String user;
    private Integer maxRetries;
    private Duration timeout;

    @NestedConfigurationProperty
    private XinferenceProxyProperties proxy;

    private Boolean logRequests;
    private Boolean logResponses;
    private Map<String, String> customHeaders;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(final String modelName) {
        this.modelName = modelName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(final String user) {
        this.user = user;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(final Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }

    public XinferenceProxyProperties getProxy() {
        return proxy;
    }

    public void setProxy(final XinferenceProxyProperties proxy) {
        this.proxy = proxy;
    }

    public Boolean getLogRequests() {
        return logRequests;
    }

    public void setLogRequests(final Boolean logRequests) {
        this.logRequests = logRequests;
    }

    public Boolean getLogResponses() {
        return logResponses;
    }

    public void setLogResponses(final Boolean logResponses) {
        this.logResponses = logResponses;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(final Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }
}
