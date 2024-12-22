package dev.langchain4j.community.qianfan.spring;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class ChatModelProperties {

    String baseUrl;
    String apiKey;
    String secretKey;
    Double temperature;
    Integer maxRetries;
    Double topP;
    String modelName;
    String endpoint;
    String responseFormat;
    Double penaltyScore;
    Boolean logRequests;
    Boolean logResponses;
}
