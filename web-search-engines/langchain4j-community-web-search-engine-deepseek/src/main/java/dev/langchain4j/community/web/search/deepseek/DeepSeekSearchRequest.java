package dev.langchain4j.community.web.search.deepseek;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
class DeepSeekSearchRequest {

    private final String query;
    private final Integer count;

    @JsonProperty("ori_question")
    private final String originalQuestion;

    private final String localization;

    @JsonProperty("need_content")
    private final Boolean needContent;

    DeepSeekSearchRequest(
            String query, Integer count, String originalQuestion, String localization, Boolean needContent) {
        this.query = query;
        this.count = count;
        this.originalQuestion = originalQuestion;
        this.localization = localization;
        this.needContent = needContent;
    }

    public String getQuery() {
        return query;
    }

    public Integer getCount() {
        return count;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public String getLocalization() {
        return localization;
    }

    public Boolean getNeedContent() {
        return needContent;
    }
}
