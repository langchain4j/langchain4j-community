package dev.langchain4j.community.model.cohere;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.CohereResponseFormatType;
import dev.langchain4j.community.model.client.chat.CohereResponseFormat;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import dev.langchain4j.community.model.client.chat.message.CohereAiMessage;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;
import dev.langchain4j.community.model.client.chat.message.CohereSystemMessage;
import dev.langchain4j.community.model.client.chat.message.CohereToolMessage;
import dev.langchain4j.community.model.client.chat.message.CohereUserMessage;
import dev.langchain4j.community.model.client.chat.message.content.CohereImageUrl;
import dev.langchain4j.community.model.client.chat.tool.CohereFunction;
import dev.langchain4j.community.model.client.chat.tool.CohereFunctionCall;
import dev.langchain4j.community.model.client.chat.tool.CohereTool;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.community.model.client.chat.tool.CohereToolType.FUNCTION;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereChatMessages;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereResponseFormat;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereTools;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereMapperTest {

    private static final String CHEDDARINI_IMAGE_URL
            = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQRHaKSxFSmgJfy3UQJeoJM0n3wHGlKtfeBoQ&s";

    private static final CohereImageUrl CHEDDARINI_IMAGE = CohereImageUrl.builder()
            .url(CHEDDARINI_IMAGE_URL)
            .detail(LOW)
            .build();

    static Stream<Arguments> expectedMessageConversions() {
        return Stream.of(
                Arguments.of(
                        singletonList(UserMessage.from("Hi!")),
                        singletonList(CohereUserMessage.from("Hi!"))
                ),

                Arguments.of(
                        asList(
                                SystemMessage.from("You are a helpful assistant."),
                                UserMessage.from("Hi!")
                        ),
                        asList(
                                CohereSystemMessage.from("You are a helpful assistant."),
                                CohereUserMessage.from("Hi!")
                        )
                ),

                Arguments.of(
                        asList(
                                UserMessage.from("Hi!"),
                                AiMessage.from("Hello!")
                        ),
                        asList(
                                CohereUserMessage.from("Hi!"),
                                CohereAiMessage.from("Hello!")
                        )
                ),

                Arguments.of(
                        asList(
                                SystemMessage.from("You are a helpful assistant."),
                                UserMessage.from("Hi!"),
                                AiMessage.from("Hello!"),
                                UserMessage.from("How are you?")
                        ),
                        asList(
                                CohereSystemMessage.from("You are a helpful assistant."),
                                CohereUserMessage.from("Hi!"),
                                CohereAiMessage.from("Hello!"),
                                CohereUserMessage.from("How are you?")
                        )
                ),

                Arguments.of(
                        asList(
                                UserMessage.from("What's the current weather in Valera like?"),
                                AiMessage.from(
                                        ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("getWeather")
                                                .arguments("{\"location\": \"Valera\"}")
                                                .build()
                                ),
                                ToolExecutionResultMessage.from("12345", "getWeather", "Rainy")
                        ),
                        asList(
                                CohereUserMessage.from("What's the current weather in Valera like?"),
                                CohereAiMessage.from(CohereToolCall.from(
                                        "12345",
                                        CohereFunctionCall.from(
                                                "getWeather",
                                                "{\"location\": \"Valera\"}")
                                )),
                                CohereToolMessage.from("12345", "Rainy")
                        )
                ),

                Arguments.of(
                        asList(
                                UserMessage.from("What's the weather like in Valera and Merida?"),
                                AiMessage.from(
                                        ToolExecutionRequest.builder()
                                                .id("12345")
                                                .name("getWeather")
                                                .arguments("{\"location\": \"Valera\"}")
                                                .build(),
                                        ToolExecutionRequest.builder()
                                                .id("6789")
                                                .name("getWeather")
                                                .arguments("{\"location\": \"Merida\"}")
                                                .build()
                                ),
                                ToolExecutionResultMessage.from("12345", "getWeather", "Rainy"),
                                ToolExecutionResultMessage.from("6789", "getWeather", "Cold")),
                        asList(
                                CohereUserMessage.from("What's the weather like in Valera and Merida?"),
                                CohereAiMessage.from(
                                        CohereToolCall.from(
                                                "12345",
                                                CohereFunctionCall.from(
                                                        "getWeather",
                                                        "{\"location\": \"Valera\"}")),
                                        CohereToolCall.from(
                                                "6789",
                                                CohereFunctionCall.from(
                                                        "getWeather",
                                                        "{\"location\": \"Merida\"}")
                                        )
                                ),
                                CohereToolMessage.from("12345", "Rainy"),
                                CohereToolMessage.from("6789", "Cold")
                        )
                ),


                Arguments.of(
                        singletonList(UserMessage.from(ImageContent.from(
                                URI.create(CHEDDARINI_IMAGE_URL)
                        ))),
                        singletonList(CohereUserMessage.from(
                                CohereContent.image(CHEDDARINI_IMAGE)
                        ))
                ),

                Arguments.of(
                        singletonList(UserMessage.from(ImageContent.from(Image.builder()
                                        .base64Data("imagedata")
                                        .mimeType("image/jpeg")
                                .build()))),
                        singletonList(CohereUserMessage.from(
                                CohereContent.image(CohereImageUrl.builder()
                                        .url("data:image/jpeg;base64,imagedata")
                                        .detail(LOW)
                                        .build())
                        ))
                ),

                Arguments.of(
                        singletonList(UserMessage.from(List.of(
                                TextContent.from("What do you see in the image?"),
                                ImageContent.from(URI.create(CHEDDARINI_IMAGE_URL))
                        ))),
                        singletonList(CohereUserMessage.from(
                                CohereContent.text("What do you see in the image?"),
                                CohereContent.image(CHEDDARINI_IMAGE)
                        ))
                )
        );
    }

    @ParameterizedTest
    @MethodSource("expectedMessageConversions")
    void should_map_messages_from_l4j_to_cohere_format(List<ChatMessage> input, List<CohereMessage> expectedOutput) {
        // when
        List<CohereMessage> cohereMessages = toCohereChatMessages(input);

        // then
        assertThat(cohereMessages).isEqualTo(expectedOutput);
    }

    @Test
    void should_map_regular_tools_from_l4j_to_cohere_format() {

        // given
        JsonObjectSchema toolSchema = JsonObjectSchema.builder()
                .addStringProperty("day", "Retrieves sales data for this day, formatted as YYYY-MM-DD.")
                .build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("queryDailySalesReport")
                .description("Connects to a database to retrieve overall sales volumes and sales information for a given day.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("day", "Retrieves sales data for this day, formatted as YYYY-MM-DD.")
                        .build())
                .build();

        CohereTool expectedTool = CohereTool.builder()
                .type(FUNCTION)
                .function(CohereFunction.builder()
                        .name("queryDailySalesReport")
                        .description("Connects to a database to retrieve overall sales volumes and sales information for a given day.")
                        .parameters(toMap(toolSchema))
                        .build())
                .build();

        // when
        List<CohereTool> result = toCohereTools(singletonList(toolSpecification));

        // then
        assertThat(result).containsExactly(expectedTool);
    }

    @Test
    void should_map_parameterless_tools_from_l4j_to_cohere_format() {

        // given
        JsonObjectSchema emptySchema = JsonObjectSchema.builder().build();

        ToolSpecification toolSpecification1 = ToolSpecification.builder()
                .name("tellJoke")
                .parameters(emptySchema)
                .build();

        ToolSpecification toolSpecification2 = ToolSpecification.builder()
                .name("tellOldSaying")
                .build();

        Map<String, Object> expectedSchema = toMap(emptySchema);

        CohereTool expectedTool1 = CohereTool.builder()
                .type(FUNCTION)
                .function(CohereFunction.builder()
                        .name("tellJoke")
                        .parameters(expectedSchema)
                        .build())
                .build();

        CohereTool expectedTool2 = CohereTool.builder()
                .type(FUNCTION)
                .function(CohereFunction.builder()
                        .name("tellOldSaying")
                        .parameters(expectedSchema)
                        .build())
                .build();

        // when
        List<CohereTool> result = toCohereTools(List.of(toolSpecification1, toolSpecification2));

        // then
        assertThat(result).containsExactly(
                expectedTool1,
                expectedTool2);
    }

    @Test
    void should_skip_mapping_response_format_if_not_specified() {

        // given - when
        CohereResponseFormat cohereResponseFormat = toCohereResponseFormat(null);

        // then
        assertThat(cohereResponseFormat).isNull();
    }

    @Test
    void should_skip_mapping_response_format_if_text_only() {

        // given - when
        CohereResponseFormat cohereResponseFormat = toCohereResponseFormat(ResponseFormat.TEXT);

        // then
        assertThat(cohereResponseFormat).isNull();
    }

    @Test
    void should_map_json_mode_to_cohere_format() {

        // given - when
        CohereResponseFormat cohereResponseFormat = toCohereResponseFormat(ResponseFormat.JSON);

        // then
        CohereResponseFormat expectedFormat = CohereResponseFormat.builder()
                .type(CohereResponseFormatType.JSON_OBJECT)
                .build();

        assertThat(cohereResponseFormat).isEqualTo(expectedFormat);
    }

    @Test
    void should_map_json_schema_to_cohere_format() {

        // given
        JsonObjectSchema actionSchema = JsonObjectSchema.builder()
                .addStringProperty("japanese")
                .addStringProperty("romaji")
                .addStringProperty("english")
                .required("japanese", "romaji", "english")
                .build();

        JsonObjectSchema rootSchema = JsonObjectSchema.builder()
                .addProperty("actions", JsonArraySchema.builder()
                        .items(actionSchema)
                        .build())
                .required("actions")
                .build();

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .rootElement(rootSchema)
                        .build())
                .build();

        // when
        CohereResponseFormat cohereResponseFormat = toCohereResponseFormat(responseFormat);

        // then
        String expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "actions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "japanese": {"type": "string"},
                          "romaji": {"type": "string"},
                          "english": {"type": "string"}
                        },
                        "required": ["japanese", "romaji", "english"]
                      }
                    }
                  },
                  "required": ["actions"]
                }
                """;

        assertThat(cohereResponseFormat.getType()).isEqualTo(CohereResponseFormatType.JSON_OBJECT);
        assertThat(cohereResponseFormat.getJsonSchema()).isEqualTo(fromJson(expectedSchema, Map.class));
    }
}
