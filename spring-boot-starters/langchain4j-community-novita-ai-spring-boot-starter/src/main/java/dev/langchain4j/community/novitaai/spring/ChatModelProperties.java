package dev.langchain4j.community.novitaai.spring;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class ChatModelProperties {
    String apiKey;
    String modelName;
    Boolean logResponses;
}
