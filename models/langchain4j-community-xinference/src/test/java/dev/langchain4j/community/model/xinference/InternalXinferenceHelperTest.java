package dev.langchain4j.community.model.xinference;

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
}
