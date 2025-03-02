package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class XinferenceStreamingChatModelIT extends AbstractInferenceChatModelInfrastructure {

    final XinferenceStreamingChatModel model = XinferenceStreamingChatModel.builder()
            .baseUrl(baseUrl())
            .apiKey(apiKey())
            .modelName(modelName())
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(Duration.ofMinutes(2))
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addIntegerProperty("first")
                    .addIntegerProperty("second")
                    .build())
            .build();

    @Test
    void should_stream_answer() throws Exception {

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat("中国首都是哪里？", handler);

        ChatResponse response = handler.get();
        assertThat(response.aiMessage().text()).containsIgnoringCase("北京");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    @Disabled(
            "Streaming support for tool calls is available only when using Qwen models with vLLM backend or GLM4-chat models without vLLM backend.")
    void should_execute_a_tool_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        model.chat(
                ChatRequest.builder()
                        .messages(singletonList(userMessage))
                        .toolSpecifications(toolSpecifications)
                        .build(),
                handler);
        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.text()).isNull();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(STOP);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();

        model.chat(messages, secondHandler);

        ChatResponse secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    @Disabled(
            "Streaming support for tool calls is available only when using Qwen models with vLLM backend or GLM4-chat models without vLLM backend.")
    void should_execute_tool_forcefully_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        // when

        model.chat(
                ChatRequest.builder()
                        .messages(singletonList(userMessage))
                        .toolSpecifications(calculator)
                        .build(),
                handler);

        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.text()).isNull();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(STOP); // not sure if a bug in OpenAI or stop is expected here

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();

        // when
        model.chat(messages, secondHandler);

        ChatResponse secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    @Disabled(
            "Streaming support for tool calls is available only when using Qwen models with vLLM backend or GLM4-chat models without vLLM backend.")
    void should_execute_multiple_tools_in_parallel_then_stream_answer() throws Exception {

        UserMessage userMessage = userMessage("2+2=? 3+3=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat(
                ChatRequest.builder()
                        .messages(singletonList(userMessage))
                        .toolSpecifications(toolSpecifications)
                        .build(),
                handler);

        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(STOP);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = from(toolExecutionRequest2, "6");

        List<ChatMessage> messages =
                asList(userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();

        model.chat(messages, secondHandler);

        ChatResponse secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    private static void assertTokenUsage(TokenUsage tokenUsage) {
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
