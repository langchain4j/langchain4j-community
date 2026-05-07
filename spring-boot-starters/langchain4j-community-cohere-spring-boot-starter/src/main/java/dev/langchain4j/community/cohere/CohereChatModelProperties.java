package dev.langchain4j.community.cohere;

import dev.langchain4j.community.model.client.CohereSafetyMode;
import dev.langchain4j.community.model.client.CohereThinkingType;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = CohereChatModelProperties.PREFIX)
public class CohereChatModelProperties {

    static final String PREFIX = "langchain4j.community.cohere.chat-model";

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private Double topP;
    private List<String> stopSequences;
    private Integer maxTokens;
    private Double presencePenalty;
    private Double frequencePenalty;
    private Long timeout;
    private Integer maxRetries;
    private CohereThinkingType thinkingType;
    private Integer thinkingTokenBudget;
    private CohereSafetyMode safetyMode;
    private Integer priority;
    private Integer seed;
    private Boolean logprobs;
    private Boolean strictTools;
    private Boolean logRequests;
    private Boolean logResponses;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getFrequencePenalty() {
        return frequencePenalty;
    }

    public void setFrequencePenalty(Double frequencePenalty) {
        this.frequencePenalty = frequencePenalty;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public CohereThinkingType getThinkingType() {
        return thinkingType;
    }

    public void setThinkingType(CohereThinkingType thinkingType) {
        this.thinkingType = thinkingType;
    }

    public Integer getThinkingTokenBudget() {
        return thinkingTokenBudget;
    }

    public void setThinkingTokenBudget(Integer thinkingTokenBudget) {
        this.thinkingTokenBudget = thinkingTokenBudget;
    }

    public CohereSafetyMode getSafetyMode() {
        return safetyMode;
    }

    public void setSafetyMode(CohereSafetyMode safetyMode) {
        this.safetyMode = safetyMode;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public Boolean getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(Boolean logprobs) {
        this.logprobs = logprobs;
    }

    public Boolean getStrictTools() {
        return strictTools;
    }

    public void setStrictTools(Boolean strictTools) {
        this.strictTools = strictTools;
    }

    public Boolean getLogRequests() {
        return logRequests;
    }

    public void setLogRequests(Boolean logRequests) {
        this.logRequests = logRequests;
    }

    public Boolean getLogResponses() {
        return logResponses;
    }

    public void setLogResponses(Boolean logResponses) {
        this.logResponses = logResponses;
    }
}
