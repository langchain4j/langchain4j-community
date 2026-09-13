package dev.langchain4j.community.model.dashscope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class QwenTokenUsageTest {

    @Test
    void should_add_cache_fields_when_both_are_qwen_usages() {
        QwenTokenUsage first = QwenTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(10)
                .cachedInputTokens(64)
                .cacheCreationInputTokens(36)
                .build();
        QwenTokenUsage second = QwenTokenUsage.builder()
                .inputTokenCount(200)
                .outputTokenCount(20)
                .cachedInputTokens(128)
                .build();

        TokenUsage sum = first.add(second);

        assertThat(sum).isInstanceOf(QwenTokenUsage.class);
        QwenTokenUsage qwenSum = (QwenTokenUsage) sum;
        assertThat(qwenSum.inputTokenCount()).isEqualTo(300);
        assertThat(qwenSum.outputTokenCount()).isEqualTo(30);
        assertThat(qwenSum.cachedInputTokens()).isEqualTo(192);
        assertThat(qwenSum.cacheCreationInputTokens()).isEqualTo(36);
    }

    @Test
    void should_keep_cache_fields_when_adding_plain_token_usage() {
        QwenTokenUsage first = QwenTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(10)
                .cachedInputTokens(64)
                .build();

        TokenUsage sum = first.add(new TokenUsage(200, 20));

        assertThat(sum).isInstanceOf(QwenTokenUsage.class);
        assertThat(((QwenTokenUsage) sum).cachedInputTokens()).isEqualTo(64);
        assertThat(((QwenTokenUsage) sum).cacheCreationInputTokens()).isNull();
    }
}
