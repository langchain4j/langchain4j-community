package dev.langchain4j.community.xinference.spring;

import dev.langchain4j.community.model.xinference.client.image.ResponseFormat;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class ImageModelProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private String negativePrompt;
    private ResponseFormat responseFormat;
    private String size;
    private String kwargs;
    private String user;
    private Integer maxRetries;
    private Duration timeout;

    @NestedConfigurationProperty
    private ProxyProperties proxy;

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

    public String getNegativePrompt() {
        return negativePrompt;
    }

    public void setNegativePrompt(final String negativePrompt) {
        this.negativePrompt = negativePrompt;
    }

    public ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(final ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getSize() {
        return size;
    }

    public void setSize(final String size) {
        this.size = size;
    }

    public String getKwargs() {
        return kwargs;
    }

    public void setKwargs(final String kwargs) {
        this.kwargs = kwargs;
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

    public ProxyProperties getProxy() {
        return proxy;
    }

    public void setProxy(final ProxyProperties proxy) {
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
