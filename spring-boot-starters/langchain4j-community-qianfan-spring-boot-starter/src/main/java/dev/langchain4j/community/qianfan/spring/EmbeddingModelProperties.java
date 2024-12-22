package dev.langchain4j.community.qianfan.spring;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class EmbeddingModelProperties {

    String baseUrl;
    String apiKey;
    String secretKey;
    Integer maxRetries;
    String modelName;
    String endpoint;
    String user;
    Boolean logRequests;
    Boolean logResponses;
}
