package dev.langchain4j.community.dashscope.spring;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class EmbeddingModelProperties {

    private String baseUrl;
    private String apiKey;
    private String modelName;
}
