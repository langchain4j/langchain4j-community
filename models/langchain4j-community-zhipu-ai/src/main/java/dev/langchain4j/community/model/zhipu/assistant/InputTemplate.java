package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputTemplate {

    private String splicingTemplate;
    private List<AssistantKeyValuePair> options;

    public String getSplicingTemplate() {
        return splicingTemplate;
    }

    public void setSplicingTemplate(String splicingTemplate) {
        this.splicingTemplate = splicingTemplate;
    }

    public List<AssistantKeyValuePair> getOptions() {
        return options;
    }

    public void setOptions(List<AssistantKeyValuePair> options) {
        this.options = options;
    }
}
