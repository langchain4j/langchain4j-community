package dev.langchain4j.community.qianfan.spring;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class LanguageModelProperties {

    String baseUrl;
    String apiKey;
    String secretKey;
    Double temperature;
    Integer maxRetries;
    Integer topK;
    Double topP;
    String modelName;
    String endpoint;
    Double penaltyScore;
    Boolean logRequests;
    Boolean logResponses;
}
