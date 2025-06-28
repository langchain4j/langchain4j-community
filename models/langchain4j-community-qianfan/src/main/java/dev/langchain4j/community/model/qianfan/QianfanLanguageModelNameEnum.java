package dev.langchain4j.community.model.qianfan;

public enum QianfanLanguageModelNameEnum {
    SQLCODER_7B("SQLCoder-7B", "sqlcoder_7b"),
    CODELLAMA_7B_INSTRUCT("CodeLlama-7b-Instruct", "codellama_7b_instruct");

    private final String modelName;
    private final String endpoint;

    QianfanLanguageModelNameEnum(String modelName, String endpoint) {
        this.modelName = modelName;
        this.endpoint = endpoint;
    }

    public static String fromModelName(String modelName) {
        for (QianfanLanguageModelNameEnum qianfanLanguageModelNameEnum : QianfanLanguageModelNameEnum.values()) {
            if (qianfanLanguageModelNameEnum.getModelName().equals(modelName)) {
                return qianfanLanguageModelNameEnum.getEndpoint();
            }
        }
        return null;
    }

    public String getModelName() {
        return modelName;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
