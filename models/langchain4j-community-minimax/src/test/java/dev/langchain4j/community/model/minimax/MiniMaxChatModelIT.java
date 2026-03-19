package dev.langchain4j.community.model.minimax;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
class MiniMaxChatModelIT {

    private static final String API_KEY = System.getenv("MINIMAX_API_KEY");

    ChatModel chatModel = MiniMaxChatModel.builder()
            .apiKey(API_KEY)
            .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
            .logRequests(true)
            .logResponses(true)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(1)
            .build();

    @Test
    void should_chat_and_return_response() {
        // given
        UserMessage userMessage = userMessage("What is 1+1? Answer with just the number.");

        // when
        ChatResponse response = chatModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("2");
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.metadata().tokenUsage().outputTokenCount()).isPositive();
        assertThat(response.metadata().finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_chat_with_system_message() {
        // given
        SystemMessage systemMessage = SystemMessage.from("You are a helpful math tutor. Be concise.");
        UserMessage userMessage = userMessage("What is the square root of 144?");

        // when
        ChatResponse response = chatModel.chat(systemMessage, userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("12");
    }

    @Test
    void should_respect_model_name_parameter() {
        // given
        ChatModel model = MiniMaxChatModel.builder()
                .apiKey(API_KEY)
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5_HIGHSPEED)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();

        // when
        ChatResponse response = model.chat(userMessage("Say hello in one word."));

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_use_temperature_parameter() {
        // given
        ChatModel model = MiniMaxChatModel.builder()
                .apiKey(API_KEY)
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();

        // when
        ChatResponse response = model.chat(userMessage("What is 2+2?"));

        // then
        assertThat(response.aiMessage().text()).contains("4");
    }

    @Test
    void should_use_max_tokens_parameter() {
        // given
        ChatModel model = MiniMaxChatModel.builder()
                .apiKey(API_KEY)
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .maxTokens(10)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();

        // when
        ChatResponse response = model.chat(userMessage("Tell me a story."));

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
        // With max 10 tokens, the response should be very short
    }

    @Test
    void should_execute_tool_then_answer() {
        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
                .name("get_weather")
                .description("Returns the current weather for a given city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city", "The city name")
                        .required("city")
                        .build())
                .build();

        List<ChatMessage> messages = List.of(
                userMessage("What's the weather in Beijing?"));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(MiniMaxChatModelName.MINIMAX_M2_5.toString())
                        .toolSpecifications(List.of(weatherTool))
                        .build())
                .build();

        // when
        ChatResponse response = chatModel.chat(request);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
        assertThat(toolRequests).hasSize(1);
        assertThat(toolRequests.get(0).name()).isEqualTo("get_weather");

        // Simulate tool execution and continue conversation
        List<ChatMessage> followUp = List.of(
                userMessage("What's the weather in Beijing?"),
                aiMessage,
                ToolExecutionResultMessage.from(toolRequests.get(0), "{\"temperature\": \"25°C\", \"condition\": \"sunny\"}"));

        ChatRequest followUpRequest = ChatRequest.builder()
                .messages(followUp)
                .parameters(ChatRequestParameters.builder()
                        .modelName(MiniMaxChatModelName.MINIMAX_M2_5.toString())
                        .build())
                .build();

        ChatResponse followUpResponse = chatModel.chat(followUpRequest);

        assertThat(followUpResponse.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_use_default_base_url() {
        // given - no baseUrl specified (should default to https://api.minimax.io/v1)
        ChatModel model = MiniMaxChatModel.builder()
                .apiKey(API_KEY)
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();

        // when
        ChatResponse response = model.chat(userMessage("Hello"));

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_support_custom_base_url() {
        // given
        ChatModel model = MiniMaxChatModel.builder()
                .baseUrl("https://api.minimax.io/")
                .apiKey(API_KEY)
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();

        // when
        ChatResponse response = model.chat(userMessage("Hello"));

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }
}
