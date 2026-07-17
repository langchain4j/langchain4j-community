package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel.GLM_4_7;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiStreamingPartialToolCallIT {

    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    private final ZhipuAiStreamingChatModel model = ZhipuAiStreamingChatModel.builder()
            .model(GLM_4_7)
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .build();

    private final ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addIntegerProperty("first")
                    .addIntegerProperty("second")
                    .build())
            .build();

    @Test
    void should_stream_partial_tool_call() {

        // given
        UserMessage userMessage = UserMessage.from("2+2=?");
        ZhipuAiChatRequestParameters parameters = ZhipuAiChatRequestParameters.builder()
                .toolSpecifications(calculator)
                .toolStream(true)
                .build();

        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());

        // when
        model.chat(
                ChatRequest.builder()
                        .messages(userMessage)
                        .parameters(parameters)
                        .build(),
                spyHandler);

        // then
        ChatResponse response = spyHandler.get();
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest request = aiMessage.toolExecutionRequests().get(0);
        assertThat(request.name()).isEqualTo("calculator");
        assertThat(request.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        verify(spyHandler, atLeastOnce()).onPartialToolCall(any());
    }
}
