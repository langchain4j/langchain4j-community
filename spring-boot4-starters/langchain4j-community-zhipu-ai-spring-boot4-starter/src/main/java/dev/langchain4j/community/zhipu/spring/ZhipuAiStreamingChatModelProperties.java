package dev.langchain4j.community.zhipu.spring;

import dev.langchain4j.community.model.zhipu.chat.Thinking;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.List;

public class ZhipuAiStreamingChatModelProperties {

    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;
    private Double topP;
    private List<String> stops;
    private ResponseFormat responseFormat;
    private Integer maxToken;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean doSample;
    private Boolean toolStream;
    private Thinking thinking;
    private Long callTimeout;
    private Long connectTimeout;
    private Long readTimeout;
    private Long writeTimeout;

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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public List<String> getStops() {
        return stops;
    }

    public void setStops(List<String> stops) {
        this.stops = stops;
    }

    public ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(ResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Integer getMaxToken() {
        return maxToken;
    }

    public void setMaxToken(Integer maxToken) {
        this.maxToken = maxToken;
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

    public Boolean getDoSample() {
        return doSample;
    }

    public void setDoSample(Boolean doSample) {
        this.doSample = doSample;
    }

    public Boolean getToolStream() {
        return toolStream;
    }

    public void setToolStream(Boolean toolStream) {
        this.toolStream = toolStream;
    }

    public Thinking getThinking() {
        return thinking;
    }

    public void setThinking(Thinking thinking) {
        this.thinking = thinking;
    }

    public Long getCallTimeout() {
        return callTimeout;
    }

    public void setCallTimeout(Long callTimeout) {
        this.callTimeout = callTimeout;
    }

    public Long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Long getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Long writeTimeout) {
        this.writeTimeout = writeTimeout;
    }
}
