package dev.langchain4j.community.model.qianfan.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public final class Usage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    public Usage() {
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(final Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(final Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(final Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Usage && this.equalTo((Usage) another);
        }
    }

    private boolean equalTo(Usage another) {
        return Objects.equals(this.promptTokens, another.promptTokens) && Objects.equals(this.completionTokens, another.completionTokens) && Objects.equals(this.totalTokens, another.totalTokens);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.promptTokens);
        h += (h << 5) + Objects.hashCode(this.completionTokens);
        h += (h << 5) + Objects.hashCode(this.totalTokens);
        return h;
    }

    @Override
    public String toString() {
        return "Usage{" +
                "promptTokens=" + promptTokens +
                ", completionTokens=" + completionTokens +
                ", totalTokens=" + totalTokens +
                '}';
    }
}

