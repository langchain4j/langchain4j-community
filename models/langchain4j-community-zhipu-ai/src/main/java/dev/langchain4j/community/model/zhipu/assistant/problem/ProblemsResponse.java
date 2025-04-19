package dev.langchain4j.community.model.zhipu.assistant.problem;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.community.model.zhipu.assistant.ClientResponse;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemsResponse implements ClientResponse<Problems> {
    private Integer code;
    private String message;
    private Problems data;

    public Integer getCode() {
        return code;
    }

    @Override
    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public Problems getData() {
        return data;
    }

    public void setData(Problems data) {
        this.data = data;
    }
}
