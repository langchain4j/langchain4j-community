package dev.langchain4j.community.model.cohere;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.CohereTokenCountEstimator;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereTokenCountEstimatorIT {

    private static final CohereTokenCountEstimator TOKEN_ESTIMATOR = CohereTokenCountEstimator.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("command-r7b-12-2024")
            .logRequests(true)
            .logResponses(true)
            .build();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void should_not_allow_blank_or_null_names_during_instantiation(String invalidModelName) {

        // given - when - then
        assertThrows(
                IllegalArgumentException.class,
                () -> CohereTokenCountEstimator.builder()
                        .modelName(invalidModelName)
                        .build());
    }

    @Test
    void should_estimate_tokens_in_plain_text() {

        // given - when
        int tokens = TOKEN_ESTIMATOR.estimateTokenCountInText("Hello there!");

        // then
        assertThat(tokens).isEqualTo(3);
    }

    @Test
    void should_throw_on_null_text_when_estimating_tokens_in_plain_text() {

        // given - when - then
        assertThrows(IllegalArgumentException.class, () -> TOKEN_ESTIMATOR.estimateTokenCountInText(null));
    }

    @Test
    void should_estimate_zero_tokens_in_empty_plain_text() {

        // given - when
        int tokens = TOKEN_ESTIMATOR.estimateTokenCountInText("");

        // then
        assertThat(tokens).isZero();
    }

    @ParameterizedTest
    @MethodSource("chatMessages")
    void should_estimate_token_count_in_any_type_of_chat_message(ChatMessage chatMessage, Integer expectedTokens) {

        // given - when
        int tokens = TOKEN_ESTIMATOR.estimateTokenCountInMessage(chatMessage);

        // then
        assertThat(tokens).isEqualTo(expectedTokens);
    }

    @Test
    void should_throw_when_estimating_tokens_in_message_if_null_message() {

        // given - when - then
        assertThrows(IllegalArgumentException.class, () -> TOKEN_ESTIMATOR.estimateTokenCountInMessage(null));
    }

    @ParameterizedTest
    @MethodSource("invalidChatMessages")
    void should_throw_when_estimating_tokens_in_message_with_unsupported_content(ChatMessage invalidChatMessage) {

        // given - when - then
        assertThrows(
                IllegalArgumentException.class, () -> TOKEN_ESTIMATOR.estimateTokenCountInMessage(invalidChatMessage));
    }

    @Test
    void should_estimate_tokens_in_chat_messages() {

        // given
        List<ChatMessage> messages = asList(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hello there!"),
                AiMessage.from("Hi!"));

        // when
        int tokens = TOKEN_ESTIMATOR.estimateTokenCountInMessages(messages);

        // then
        assertThat(tokens).isEqualTo(10);
    }

    @Test
    void should_estimate_zero_tokens_in_empty_chat_messages() {

        // given - when
        int tokens = TOKEN_ESTIMATOR.estimateTokenCountInMessages(emptyList());

        // then
        assertThat(tokens).isZero();
    }

    @Test
    void should_throw_while_estimating_tokens_in_chat_messages_if_null_messages() {

        // given - when - then
        assertThrows(IllegalArgumentException.class, () -> TOKEN_ESTIMATOR.estimateTokenCountInMessages(null));
    }

    static Stream<Arguments> chatMessages() {
        ToolExecutionRequest weatherToolRequest = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getWeather")
                .arguments("{\"city\":\"Valera\"}")
                .build();

        return Stream.of(
                Arguments.of(SystemMessage.from("You are a helpful assistant"), 5),
                Arguments.of(UserMessage.from("Hello there!"), 3),
                Arguments.of(
                        UserMessage.from(asList(
                                TextContent.from("Hello there!"),
                                TextContent.from("Hello there!"),
                                TextContent.from("Hello there!"))),
                        11),
                Arguments.of(AiMessage.from("What's up?"), 4),
                Arguments.of(
                        AiMessage.builder().thinking("The user is greeting me").build(), 5),
                Arguments.of(AiMessage.from(weatherToolRequest), 13),
                Arguments.of(
                        AiMessage.builder()
                                .text("Let me check the weather.")
                                .thinking("The user is greeting me")
                                .toolExecutionRequests(List.of(weatherToolRequest))
                                .build(),
                        26),
                Arguments.of(ToolExecutionResultMessage.from(weatherToolRequest, "22 degrees and sunny"), 5));
    }

    static Stream<Arguments> invalidChatMessages() {
        return Stream.of(
                Arguments.of(UserMessage.from(ImageContent.from("https://example.com/cat.png"))),
                Arguments.of(UserMessage.from(AudioContent.from("https://example.com/sound.mp3"))),
                Arguments.of(UserMessage.from(VideoContent.from("https://example.com/clip.mp4"))),
                Arguments.of(UserMessage.from(PdfFileContent.from("https://example.com/doc.pdf"))),
                Arguments.of(UserMessage.from(
                        TextContent.from("Describe this"), ImageContent.from("https://example.com/cat.png"))),
                Arguments.of(CustomMessage.from(Map.of("custom", "message"))));
    }
}
