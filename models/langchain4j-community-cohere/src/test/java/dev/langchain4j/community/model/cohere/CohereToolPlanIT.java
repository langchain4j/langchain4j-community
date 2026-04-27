package dev.langchain4j.community.model.cohere;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereToolPlanIT {

    private static final ChatModel CHAT_MODEL = CohereChatModel.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("command-a-03-2025")
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final StreamingChatModel STREAMING_CHAT_MODEL = CohereStreamingChatModel.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("command-a-03-2025")
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final ToolSpecification DEFAULT_TOOL = ToolSpecification.builder()
            .name("translateText")
            .description("Translates text from one language into another")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("inputLanguage")
                    .addStringProperty("outputLanguage")
                    .build())
            .build();

    private static final String USER_PROMPT = "Translate 'Hello, how are you' to German";

    @Test
    void should_include_tool_plan_when_chat_model_has_defined_tools() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(USER_PROMPT))
                .toolSpecifications(DEFAULT_TOOL)
                .build();

        // when
        ChatResponse response = CHAT_MODEL.chat(chatRequest);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.attributes().get("tool_plan")).isNotNull();
        assertThat((String) aiMessage.attributes().get("tool_plan")).isNotBlank();
    }

    @Test
    void should_NOT_include_tool_plan_when_chat_model_does_NOT_have_defined_tools() {

        // given
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(USER_PROMPT)).build();

        // when
        ChatResponse response = CHAT_MODEL.chat(chatRequest);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.attributes().get("tool_plan")).isNull();
    }

    @Test
    void should_include_tool_plan_when_streaming_model_has_defined_tools() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(USER_PROMPT))
                .toolSpecifications(DEFAULT_TOOL)
                .build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        STREAMING_CHAT_MODEL.chat(chatRequest, handler);
        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.attributes().get("tool_plan")).isNotNull();
        assertThat((String) aiMessage.attributes().get("tool_plan")).isNotBlank();
    }

    @Test
    void should_NOT_include_tool_plan_when_streaming_model_does_NOT_have_defined_tools() {

        // given
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(USER_PROMPT)).build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        STREAMING_CHAT_MODEL.chat(chatRequest, handler);
        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.attributes().get("tool_plan")).isNull();
    }
}
