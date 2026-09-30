package dev.langchain4j.community.model.xinference;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.xinference.client.chat.Delta;
import dev.langchain4j.community.model.xinference.client.chat.message.FunctionCall;
import dev.langchain4j.community.model.xinference.client.chat.message.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class XinferenceStreamingResponseBuilderTest {

    private static ChatCompletionResponse responseOf(Delta delta) {
        return ChatCompletionResponse.builder()
                .choices(List.of(ChatCompletionChoice.builder().delta(delta).build()))
                .build();
    }

    @Test
    void should_accumulate_reasoning_content_across_deltas() {

        // given
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder(true);

        // when
        responseBuilder.append(
                responseOf(Delta.builder().reasoningContent("The user ").build()));
        responseBuilder.append(
                responseOf(Delta.builder().reasoningContent("asks about Spain.").build()));
        responseBuilder.append(responseOf(Delta.builder().content("Madrid").build()));

        // then
        var aiMessage = responseBuilder.build().aiMessage();
        assertThat(aiMessage.thinking()).isEqualTo("The user asks about Spain.");
        assertThat(aiMessage.text()).isEqualTo("Madrid");
    }

    @Test
    void should_NOT_parse_reasoning_content_when_not_requested() {

        // given
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder(false);

        // when
        responseBuilder.append(responseOf(Delta.builder()
                .reasoningContent("hidden reasoning")
                .content("visible answer")
                .build()));

        // then
        var aiMessage = responseBuilder.build().aiMessage();
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.text()).isEqualTo("visible answer");
    }

    @Test
    void should_never_build_an_all_null_ai_message_when_the_stream_is_empty() {

        // given
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder(true);

        // when
        var aiMessage = responseBuilder.build().aiMessage();

        // then
        assertThat(aiMessage.text()).isEqualTo("");
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.hasToolExecutionRequests()).isFalse();
    }

    @Test
    void should_keep_whitespace_only_thinking_consistent_with_the_partial_thinking_gate() {

        // given
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder(true);

        // when — a whitespace-only delta fires onPartialThinking (non-empty), so it must be kept in the final message
        // too
        responseBuilder.append(responseOf(Delta.builder().reasoningContent("  ").build()));

        // then
        assertThat(responseBuilder.build().aiMessage().thinking()).isEqualTo("  ");
    }

    @Test
    void should_map_blank_text_to_null_only_when_tool_calls_are_present() {

        // given
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder(false);

        // when
        responseBuilder.append(responseOf(Delta.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call_1")
                        .function(FunctionCall.builder()
                                .name("get_weather")
                                .arguments("{\"city\":\"Madrid\"}")
                                .build())
                        .build()))
                .build()));

        // then
        var aiMessage = responseBuilder.build().aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
    }
}
