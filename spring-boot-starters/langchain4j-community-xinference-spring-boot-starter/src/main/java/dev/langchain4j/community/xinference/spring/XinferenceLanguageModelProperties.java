package dev.langchain4j.community.xinference.spring;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = XinferenceLanguageModelProperties.PREFIX)
public class XinferenceLanguageModelProperties {
    static final String PREFIX = "langchain4j.community.xinference.language-model";

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Double temperature;
    private Double topP;
    private Integer logprobs;
    private Boolean echo;
    private List<String> stop;
    private Double presencePenalty;
    private Double frequencyPenalty;
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

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(final Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(final Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(final Double topP) {
        this.topP = topP;
    }

    public Integer getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(final Integer logprobs) {
        this.logprobs = logprobs;
    }

    public Boolean getEcho() {
        return echo;
    }

    public void setEcho(final Boolean echo) {
        this.echo = echo;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(final List<String> stop) {
        this.stop = stop;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(final Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(final Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
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
