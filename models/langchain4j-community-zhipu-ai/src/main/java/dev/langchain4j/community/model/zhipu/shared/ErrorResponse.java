package dev.langchain4j.community.model.zhipu.shared;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorResponse {

    private List<SensitiveFilter> contentFilter;
    private Map<String, String> error;

    public Map<String, String> getError() {
        return error;
    }

    public void setError(Map<String, String> error) {
        this.error = error;
    }

    public List<SensitiveFilter> getContentFilter() {
        return contentFilter;
    }

    public void setContentFilter(List<SensitiveFilter> contentFilter) {
        this.contentFilter = contentFilter;
    }
}
