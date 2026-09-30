package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.aiMessageFrom;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toTool;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toXinferenceMessages;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.xinference.client.chat.Function;
import dev.langchain4j.community.model.xinference.client.chat.Parameters;
import dev.langchain4j.community.model.xinference.client.chat.Tool;
import dev.langchain4j.community.model.xinference.client.chat.message.AssistantMessage;
import dev.langchain4j.community.model.xinference.client.chat.message.Message;
import dev.langchain4j.community.model.xinference.client.chat.message.ToolCall;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InternalXinferenceHelperTest {

    @Test
    void should_throw_when_mapping_messages_if_tool_execution_result_does_NOT_have_text_content_exclusively() {

        // given
        List<ChatMessage> messages = List.of(ToolExecutionResultMessage.builder()
                .id("call_1")
                .toolName("get_weather_map")
                .contents(ImageContent.from("https://example.com/weather-map.png"))
                .build());

        // when - then
        assertThatThrownBy(() -> toXinferenceMessages(messages)).isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void should_NOT_throw_when_mapping_messages_if_tool_execution_result_has_text_content_exclusively() {

        // given
        List<ChatMessage> messages = List.of(ToolExecutionResultMessage.builder()
                .id("call_1")
                .toolName("get_weather")
                .text("Sunny, 25°C")
                .build());

        // when - then
        assertThatNoException().isThrownBy(() -> toXinferenceMessages(messages));
    }

    @ParameterizedTest
    @MethodSource("toolSpecifications")
    void should_map_tools_to_xinference_format(ToolSpecification input, Tool expectedOutput) {

        // given - when
        Tool tool = toTool(input);

        // then
        assertThat(tool).usingRecursiveComparison().isEqualTo(expectedOutput);
    }

    static Stream<Arguments> toolSpecifications() {
        return Stream.of(

                // Tool with all properties defined.
                Arguments.of(
                        ToolSpecification.builder()
                                .name("get_weather")
                                .description("Returns the current weather for a city")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("city", "Name of the city")
                                        .required("city")
                                        .build())
                                .build(),
                        Tool.of(Function.builder()
                                .name("get_weather")
                                .description("Returns the current weather for a city")
                                .parameters(Parameters.builder()
                                        .type("object")
                                        .properties(Map.of(
                                                "city",
                                                Map.of(
                                                        "type", "string",
                                                        "description", "Name of the city")))
                                        .required(List.of("city"))
                                        .build())
                                .build())),

                // Tool without description
                Arguments.of(
                        ToolSpecification.builder()
                                .name("get_stock_price")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("ticker", "Stock ticker symbol")
                                        .required("ticker")
                                        .build())
                                .build(),
                        Tool.of(Function.builder()
                                .name("get_stock_price")
                                .parameters(Parameters.builder()
                                        .type("object")
                                        .properties(Map.of(
                                                "ticker",
                                                Map.of(
                                                        "type", "string",
                                                        "description", "Stock ticker symbol")))
                                        .required(List.of("ticker"))
                                        .build())
                                .build())),

                // Tool without parameters/empty schema
                Arguments.of(
                        ToolSpecification.builder()
                                .name("get_current_time")
                                .description("Returns the current time")
                                .build(),
                        Tool.of(Function.builder()
                                .name("get_current_time")
                                .description("Returns the current time")
                                .parameters(Parameters.builder()
                                        .type("object")
                                        .properties(emptyMap())
                                        .build())
                                .build())));
    }

    @Test
    void should_map_empty_content_to_empty_text_in_ai_message() {

        // given — AssistantMessage defaults null content to ""
        AssistantMessage assistantMessage = AssistantMessage.builder().build();

        // when
        AiMessage aiMessage = aiMessageFrom(assistantMessage, true);

        // then
        assertThat(aiMessage.text()).isEqualTo("");
        assertThat(aiMessage.thinking()).isNull();
    }

    @Test
    void should_include_thinking_only_when_requested() {

        // given
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("Madrid")
                .reasoningContent("The capital of Spain is Madrid")
                .build();

        // when - then
        assertThat(aiMessageFrom(assistantMessage, true).thinking()).isEqualTo("The capital of Spain is Madrid");
        assertThat(aiMessageFrom(assistantMessage, false).thinking()).isNull();
        assertThat(aiMessageFrom(assistantMessage, false).text()).isEqualTo("Madrid");
    }

    @Test
    void should_preserve_whitespace_only_thinking_to_match_the_partial_thinking_gate() {

        // given
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("Madrid")
                .reasoningContent("  ")
                .build();

        // when - then
        assertThat(aiMessageFrom(assistantMessage, true).thinking()).isEqualTo("  ");
    }

    @Test
    void should_throw_when_all_tool_calls_are_filtered_out_by_type() {

        // given — a tool call with an absent (non-FUNCTION) type gets filtered out, leaving an empty list
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("I will call a tool")
                .toolCalls(List.of(ToolCall.builder().id("call_1").build()))
                .build();

        // when - then
        assertThatThrownBy(() -> aiMessageFrom(assistantMessage, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("function");
    }

    @Test
    void should_keep_content_non_null_when_round_tripping_a_thinking_only_ai_message() {

        // given — a thinking-only AiMessage, as produced by an assistant turn with reasoning and empty content
        AiMessage aiMessage = AiMessage.builder().thinking("some reasoning").build();

        // when
        Message message = toXinferenceMessages(List.of(aiMessage)).get(0);

        // then — the serialized outbound assistant turn must never carry null content
        assertThat(((AssistantMessage) message).getContent()).isEqualTo("");
    }
}
