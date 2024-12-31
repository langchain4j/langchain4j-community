package dev.langchain4j.community.xinference.spring;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = StreamingChatModelProperties.PREFIX)
public class StreamingChatModelProperties {
    static final String PREFIX = "langchain4j.community.xinference.streaming-chat-model";

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private Double topP;
    private List<String> stop;
    private Integer maxTokens;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Integer seed;
    private String user;
    private Object toolChoice;
    private Boolean parallelToolCalls;
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

    public List<String> getStop() {
        return stop;
    }

    public void setStop(final List<String> stop) {
        this.stop = stop;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(final Integer maxTokens) {
        this.maxTokens = maxTokens;
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

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(final Integer seed) {
        this.seed = seed;
    }

    public String getUser() {
        return user;
    }

    public void setUser(final String user) {
        this.user = user;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(final Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(final Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
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
