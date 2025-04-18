package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenTokenCountEstimatorIT {

    private QwenTokenCountEstimator tokenCountEstimator;

    @BeforeEach
    void setUp() {
        tokenCountEstimator = QwenTokenCountEstimator.builder()
                .apiKey(apiKey())
                .modelName(QwenModelName.QWEN_PLUS)
                .build();
    }

    @ParameterizedTest
    @MethodSource
    void should_count_tokens_in_messages(List<ChatMessage> messages, int expectedTokenCount) {
        int tokenCount = tokenCountEstimator.estimateTokenCountInMessages(messages);
        assertThat(tokenCount).isEqualTo(expectedTokenCount);
    }

    static Stream<Arguments> should_count_tokens_in_messages() {
        return Stream.of(
                Arguments.of(singletonList(userMessage("hello")), 1),
                Arguments.of(singletonList(userMessage("Klaus", "hello")), 1),
                Arguments.of(asList(userMessage("hello"), aiMessage("hi there"), userMessage("bye")), 4));
    }

    @Test
    void should_count_tokens_in_short_texts() {
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello!")).isEqualTo(2);
        assertThat(tokenCountEstimator.estimateTokenCountInText("Hello, how are you?"))
                .isEqualTo(6);

        assertThat(tokenCountEstimator.estimateTokenCountInText("")).isEqualTo(0);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n\n")).isEqualTo(1);
        assertThat(tokenCountEstimator.estimateTokenCountInText("\n \n\n")).isEqualTo(2);
    }

    @Test
    void should_count_tokens_in_short_texts_by_customized_request() {
        tokenCountEstimator.setGenerationParamCustomizer(generationParamBuilder -> {
            generationParamBuilder.prompt("{placeholder}");
        });

        assertThat(tokenCountEstimator.estimateTokenCountInText("")).isPositive();
    }

    @Test
    void should_count_tokens_in_average_text() {
        String text1 = "Hello, how are you doing? What do you want to talk about?";
        assertThat(tokenCountEstimator.estimateTokenCountInText(text1)).isEqualTo(15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 2));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text2)).isEqualTo(2 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 3));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text3)).isEqualTo(3 * 15);
    }

    @Test
    void should_count_tokens_in_large_text() {
        String text1 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 10));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text1)).isEqualTo(10 * 15);

        String text2 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 50));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text2)).isEqualTo(50 * 15);

        String text3 = String.join(" ", repeat("Hello, how are you doing? What do you want to talk about?", 100));
        assertThat(tokenCountEstimator.estimateTokenCountInText(text3)).isEqualTo(100 * 15);
    }

    @Test
    void should_count_empty_messages_and_return_0() {
        assertThat(tokenCountEstimator.estimateTokenCountInMessages(null)).isZero();
        assertThat(tokenCountEstimator.estimateTokenCountInMessages(emptyList()))
                .isZero();
    }

    public static List<String> repeat(String s, int n) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(s);
        }
        return result;
    }
}
