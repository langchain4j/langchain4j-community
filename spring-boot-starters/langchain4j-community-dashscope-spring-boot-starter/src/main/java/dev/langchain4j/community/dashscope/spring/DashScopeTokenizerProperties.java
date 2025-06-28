package dev.langchain4j.community.dashscope.spring;

public class DashScopeTokenizerProperties {

    private String apiKey;
    private String modelName;

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
}
