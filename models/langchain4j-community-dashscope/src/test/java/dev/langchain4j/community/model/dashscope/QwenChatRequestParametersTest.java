package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_MAX;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.Test;

public class QwenChatRequestParametersTest {
    @Test
    void test_override_with() {
        // given
        QwenChatRequestParameters original = QwenChatRequestParameters.builder()
                .modelName(QWEN_MAX)
                .temperature(0.7)
                .maxOutputTokens(500)
                .seed(42)
                .enableSearch(false)
                .build();

        QwenChatRequestParameters override = QwenChatRequestParameters.builder()
                .seed(50)
                .enableSearch(true)
                .searchOptions(QwenChatRequestParameters.SearchOptions.builder()
                        .citationFormat("[<number>]")
                        .enableCitation(true)
                        .enableSource(true)
                        .forcedSearch(true)
                        .searchStrategy("standard")
                        .build())
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result).isInstanceOf(QwenChatRequestParameters.class);
        QwenChatRequestParameters qwenResult = (QwenChatRequestParameters) result;

        assertThat(qwenResult.modelName()).isEqualTo(QWEN_MAX);
        assertThat(qwenResult.temperature()).isEqualTo(0.7);
        assertThat(qwenResult.maxOutputTokens()).isEqualTo(500);
        assertThat(qwenResult.seed()).isEqualTo(50);
        assertThat(qwenResult.enableSearch()).isTrue();
        assertThat(qwenResult.searchOptions()).isNotNull();
        assertThat(qwenResult.searchOptions().citationFormat()).isEqualTo("[<number>]");
        assertThat(qwenResult.searchOptions().enableCitation()).isTrue();
        assertThat(qwenResult.searchOptions().enableSource()).isTrue();
        assertThat(qwenResult.searchOptions().forcedSearch()).isTrue();
        assertThat(qwenResult.searchOptions().searchStrategy()).isEqualTo("standard");
    }
}
