package dev.langchain4j.community.model.xinference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "XINFERENCE_BASE_URL", matches = ".+")
class XinferenceStreamingChatModelIT extends AbstractInferenceLanguageModelInfrastructure {


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

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate("中国首都是哪座城市?", handler);

        Response<AiMessage> response = handler.get();
        assertThat(response.content().text()).containsIgnoringCase("北京");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_a_tool_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        // when
        model.generate(singletonList(userMessage), toolSpecifications, handler);
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();

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
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();

        model.generate(messages, secondHandler);

        Response<AiMessage> secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.content();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_tool_forcefully_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        // when

        model.generate(singletonList(userMessage), calculator, handler);

        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();

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
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();

        // when
        model.generate(messages, secondHandler);

        Response<AiMessage> secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.content();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_stream_answer() throws Exception {

        UserMessage userMessage = userMessage("2+2=? 3+3=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate(singletonList(userMessage), toolSpecifications, handler);

        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();

        // then
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 = aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(STOP);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = from(toolExecutionRequest2, "6");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when
        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();

        model.generate(messages, secondHandler);

        Response<AiMessage> secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.content();

        // then
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }


    private static void assertTokenUsage(TokenUsage tokenUsage) {
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
