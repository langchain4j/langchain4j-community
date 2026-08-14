package dev.langchain4j.community.dashscope.spring;

public class DashScopeScoringModelProperties {

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer topN;
    private Boolean returnDocuments;
    private String instruct;

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

    public Integer getTopN() {
        return topN;
    }

    public void setTopN(Integer topN) {
        this.topN = topN;
    }

    public Boolean getReturnDocuments() {
        return returnDocuments;
    }

    public void setReturnDocuments(Boolean returnDocuments) {
        this.returnDocuments = returnDocuments;
    }

    public String getInstruct() {
        return instruct;
    }

    public void setInstruct(String instruct) {
        this.instruct = instruct;
    }
}
