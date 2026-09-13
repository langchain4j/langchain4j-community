package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

/**
 * Represents {@link TokenUsage} for DashScope (Qwen) models.
 * In addition to {@link TokenUsage}, contains information about the context cache:
 * the number of input tokens read from the cache and the number of input tokens written to it.
 * Corresponds to the {@code usage.prompt_tokens_details} field of the DashScope API.
 */
public class QwenTokenUsage extends TokenUsage {

    private final Integer cachedInputTokens;
    private final Integer cacheCreationInputTokens;

    private QwenTokenUsage(Builder builder) {
        super(builder.inputTokenCount, builder.outputTokenCount, builder.totalTokenCount);
        this.cachedInputTokens = builder.cachedInputTokens;
        this.cacheCreationInputTokens = builder.cacheCreationInputTokens;
    }

    /**
     * Returns the number of input tokens read from the context cache
     * ({@code usage.prompt_tokens_details.cached_tokens}), or {@code null} if not reported.
     */
    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }

    /**
     * Returns the number of input tokens written to the explicit context cache
     * ({@code usage.prompt_tokens_details.cache_creation_input_tokens}), or {@code null} if not reported.
     */
    public Integer cacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public QwenTokenUsage add(TokenUsage that) {
        if (that == null) {
            return this;
        }

        return builder()
                .inputTokenCount(sum(this.inputTokenCount(), that.inputTokenCount()))
                .outputTokenCount(sum(this.outputTokenCount(), that.outputTokenCount()))
                .cachedInputTokens(addCachedInputTokens(that))
                .cacheCreationInputTokens(addCacheCreationInputTokens(that))
                .build();
    }

    private Integer addCachedInputTokens(TokenUsage that) {
        if (that instanceof QwenTokenUsage thatQwenTokenUsage) {
            return sum(this.cachedInputTokens, thatQwenTokenUsage.cachedInputTokens);
        } else {
            return this.cachedInputTokens;
        }
    }

    private Integer addCacheCreationInputTokens(TokenUsage that) {
        if (that instanceof QwenTokenUsage thatQwenTokenUsage) {
            return sum(this.cacheCreationInputTokens, thatQwenTokenUsage.cacheCreationInputTokens);
        } else {
            return this.cacheCreationInputTokens;
        }
    }

    public static class Builder {

        private Integer inputTokenCount;
        private Integer outputTokenCount;
        private Integer totalTokenCount;
        private Integer cachedInputTokens;
        private Integer cacheCreationInputTokens;

        public Builder inputTokenCount(Integer inputTokenCount) {
            this.inputTokenCount = inputTokenCount;
            return this;
        }

        public Builder outputTokenCount(Integer outputTokenCount) {
            this.outputTokenCount = outputTokenCount;
            return this;
        }

        public Builder totalTokenCount(Integer totalTokenCount) {
            this.totalTokenCount = totalTokenCount;
            return this;
        }

        public Builder cachedInputTokens(Integer cachedInputTokens) {
            this.cachedInputTokens = cachedInputTokens;
            return this;
        }

        public Builder cacheCreationInputTokens(Integer cacheCreationInputTokens) {
            this.cacheCreationInputTokens = cacheCreationInputTokens;
            return this;
        }

        public QwenTokenUsage build() {
            return new QwenTokenUsage(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        QwenTokenUsage that = (QwenTokenUsage) o;
        return Objects.equals(cachedInputTokens, that.cachedInputTokens)
                && Objects.equals(cacheCreationInputTokens, that.cacheCreationInputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cachedInputTokens, cacheCreationInputTokens);
    }

    @Override
    public String toString() {
        return "QwenTokenUsage {"
                + "inputTokenCount = " + inputTokenCount()
                + ", outputTokenCount = " + outputTokenCount()
                + ", totalTokenCount = " + totalTokenCount()
                + ", cachedInputTokens = " + cachedInputTokens
                + ", cacheCreationInputTokens = " + cacheCreationInputTokens
                + " }";
    }
}
