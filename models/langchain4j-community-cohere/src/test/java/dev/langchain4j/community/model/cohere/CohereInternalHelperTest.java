package dev.langchain4j.community.model.cohere;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import dev.langchain4j.community.model.client.chat.message.CohereUserMessage;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.response.CohereResponseMessage;
import dev.langchain4j.community.model.client.chat.response.CohereTokens;
import dev.langchain4j.community.model.client.chat.response.CohereUsage;
import dev.langchain4j.community.model.client.chat.thinking.CohereThinking;
import dev.langchain4j.community.model.client.chat.tool.CohereFunctionCall;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;
import dev.langchain4j.community.model.client.CohereChatRequestParameters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.community.model.util.CohereInternalHelper.fromCohereChatResponse;
import static dev.langchain4j.community.model.util.CohereInternalHelper.toCohereChatRequest;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereInternalHelperTest {

    private static final String MODEL_NAME = "command-r7b-12-2024";

    static Stream<Arguments> expectedParameterConversions() {
        return Stream.of(

                // Just messages
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder().modelName(MODEL_NAME).build(),
                        CohereChatRequest.builder().model(MODEL_NAME).messages(CohereUserMessage.from("User message")).build()
                ),

                // Messages with common L4J parameters
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .temperature(0.3)
                                .topP(0.8)
                                .topK(40)
                                .presencePenalty(0.2)
                                .frequencyPenalty(0.1)
                                .maxOutputTokens(300)
                                .stopSequences(asList("STOP_1", "STOP_2"))
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .temperature(0.3)
                                .p(0.8)
                                .k(40)
                                .presencePenalty(0.2)
                                .frequencyPenalty(0.1)
                                .maxTokens(300)
                                .stopSequences(asList("STOP_1", "STOP_2"))
                                .build()
                ),

                // With Streaming (with just messages)
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .stream(true)
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .stream(true)
                                .build()
                ),

                // With thinking type defined (but no token budget)
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .thinkingType("enabled")
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .thinking(CohereThinking.builder().type("enabled").build())
                                .build()
                ),

                // With token budget defined (but no thinking type)
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .thinkingTokenBudget(128)
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .thinking(CohereThinking.builder().tokenBudget(128).build())
                                .build()
                ),

                // With safety mode
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .safetyMode("CONTEXTUAL")
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .safetyMode("CONTEXTUAL")
                                .build()
                ),

                // With priority
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .priority(999)
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .priority(999)
                                .build()
                ),

                // With seed
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .seed(99)
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .seed(99)
                                .build()
                ),

                // With logprobs
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .logprobs(true)
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .logprobs(true)
                                .build()
                ),

                // With strict tools
                Arguments.of(
                        singletonList(UserMessage.from("User message")),
                        CohereChatRequestParameters.builder()
                                .modelName(MODEL_NAME)
                                .strictTools(true)
                                .build(),
                        CohereChatRequest.builder()
                                .model(MODEL_NAME)
                                .messages(CohereUserMessage.from("User message"))
                                .strictTools(true)
                                .build()
                )
        );
    }

    static Stream<Arguments> expectedResponseDeconversions() {
        return Stream.of(

                // Just text, no tools
                Arguments.of(
                        CohereChatResponse.builder()
                                .id("12345")
                                .finishReason("COMPLETE")
                                .usage(CohereUsage.builder()
                                        .tokens(CohereTokens.of(1.0, 1.0))
                                        .build())
                                .message(CohereResponseMessage.builder()
                                        .content(singletonList(CohereContent.text("Text response")))
                                        .build())
                                .build(),
                        ChatResponse.builder()
                                .aiMessage(AiMessage.builder()
                                        .text("Text response")
                                        .build())
                                .metadata(ChatResponseMetadata.builder()
                                        .modelName(MODEL_NAME)
                                        .id("12345")
                                        .tokenUsage(new TokenUsage(1, 1))
                                        .finishReason(STOP)
                                        .build())
                                .build()
                ),

                // Text + thinking, no tools.
                Arguments.of(
                        CohereChatResponse.builder()
                                .id("12345")
                                .finishReason("COMPLETE")
                                .usage(CohereUsage.builder()
                                        .tokens(CohereTokens.of(1.0, 1.0))
                                        .build())
                                .message(CohereResponseMessage.builder()
                                        .content(asList(
                                                CohereContent.thinking("Thinking response 1"),
                                                CohereContent.text("Text response 1"),
                                                CohereContent.thinking("Thinking response 2"),
                                                CohereContent.text("Text response 2")
                                        ))
                                        .build())
                                .build(),
                        ChatResponse.builder()
                                .aiMessage(AiMessage.builder()
                                        .text("Text response 1\nText response 2")
                                        .thinking("Thinking response 1\nThinking response 2")
                                        .build())
                                .metadata(ChatResponseMetadata.builder()
                                        .modelName(MODEL_NAME)
                                        .id("12345")
                                        .tokenUsage(new TokenUsage(1, 1 ))
                                        .finishReason(STOP)
                                        .build())
                                .build()
                ),

                // Just tool calls, neither text nor thinking.
                Arguments.of(
                        CohereChatResponse.builder()
                                .id("12345")
                                .finishReason("TOOL_CALL")
                                .usage(CohereUsage.builder()
                                        .tokens(CohereTokens.of(1.0, 1.0))
                                        .build())
                                .message(CohereResponseMessage.builder()
                                        .content(emptyList())
                                        .toolCalls(asList(
                                                CohereToolCall.from("tool_1", CohereFunctionCall.from("getWeather", "{\"city\":\"Valera\"}")),
                                                CohereToolCall.from("tool_2", CohereFunctionCall.from("getWeather", "{\"city\":\"Merida\"}"))
                                        ))
                                        .build())
                                .build(),
                        ChatResponse.builder()
                                .aiMessage(AiMessage.builder()
                                        .text(null)
                                        .thinking(null)
                                        .toolExecutionRequests(asList(
                                                ToolExecutionRequest.builder()
                                                        .id("tool_1")
                                                        .name("getWeather")
                                                        .arguments("{\"city\":\"Valera\"}")
                                                        .build(),
                                                ToolExecutionRequest.builder()
                                                        .id("tool_2")
                                                        .name("getWeather")
                                                        .arguments("{\"city\":\"Merida\"}")
                                                        .build()
                                        ))
                                        .build())
                                .metadata(ChatResponseMetadata.builder()
                                        .modelName(MODEL_NAME)
                                        .id("12345")
                                        .tokenUsage(new TokenUsage(1, 1))
                                        .finishReason(TOOL_EXECUTION)
                                        .build())
                                .build()
                )
        );
    }

    @ParameterizedTest
    @MethodSource("expectedParameterConversions")
    void should_convert_chat_requests_to_cohere_format(List<ChatMessage> chatMessages,
                                                       CohereChatRequestParameters inputParameters,
                                                       CohereChatRequest expectedRequest) {

        // given - when
        CohereChatRequest result = toCohereChatRequest(chatMessages, inputParameters);

        // then
        assertThat(result).isEqualTo(expectedRequest);
    }

    @ParameterizedTest
    @MethodSource("expectedResponseDeconversions")
    void should_convert_cohere_responses_to_l4j_format(CohereChatResponse cohereResponse,
                                                       ChatResponse expectedResponse) {

        // given - when
        ChatResponse response = fromCohereChatResponse(cohereResponse, MODEL_NAME);

        // then
        assertThat(response).isEqualTo(expectedResponse);
    }
}
