package dev.langchain4j.community.dashscope.spring;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class ChatModelProperties {

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double topP;
    private Integer topK;
    private Boolean enableSearch;
    private Integer seed;
    private Float repetitionPenalty;
    private Float temperature;
    private List<String> stops;
    private Integer maxTokens;
}
