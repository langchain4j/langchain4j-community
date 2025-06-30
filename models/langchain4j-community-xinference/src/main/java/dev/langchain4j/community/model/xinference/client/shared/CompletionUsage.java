package dev.langchain4j.community.model.xinference.client.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = CompletionUsage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionUsage {
    private final Integer totalTokens;
    private final Integer promptTokens;
    private final Integer completionTokens;

    private CompletionUsage(Builder builder) {
        totalTokens = builder.totalTokens;
        promptTokens = builder.promptTokens;
        completionTokens = builder.completionTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Integer totalTokens;
        private Integer promptTokens;
        private Integer completionTokens;

        private Builder() {}

        public Builder totalTokens(Integer val) {
            totalTokens = val;
            return this;
        }

        public Builder promptTokens(Integer val) {
            promptTokens = val;
            return this;
        }

        public Builder completionTokens(Integer val) {
            completionTokens = val;
            return this;
        }

        public CompletionUsage build() {
            return new CompletionUsage(this);
        }
    }
}
