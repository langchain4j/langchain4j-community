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
public class AssistantSupportResponse implements ClientResponse<List<AssistantKeyValuePair>> {
    private int code;
    private String message;
    private List<AssistantKeyValuePair> data;

    public int getCode() {
        return code;
    }

    @Override
    public void setCode(final int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void setMessage(final String message) {
        this.message = message;
    }

    @Override
    public List<AssistantKeyValuePair> getData() {
        return data;
    }

    @Override
    public void setData(final List<AssistantKeyValuePair> data) {
        this.data = data;
    }
}
