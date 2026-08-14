package dev.langchain4j.community.model.oracle.oci.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.CohereChatBotMessage;
import com.oracle.bmc.generativeaiinference.model.CohereToolMessage;
import com.oracle.bmc.generativeaiinference.model.CohereUserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseCohereChatModelTest {

    @Test
    void prepareRequestShouldPreserveAssistantTextInChatHistory() {
        var syncClient = mock(GenerativeAiInferenceClient.class);

        try (var model = OciGenAiCohereChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .build()) {

            var request = model.prepareRequest(ChatRequest.builder()
                            .messages(
                                    UserMessage.from("Hi, my favorite color is green"),
                                    AiMessage.from("Hi, nice to meet you"),
                                    UserMessage.from("What is my favorite color?"))
                            .build())
                    .build();

            assertEquals("What is my favorite color?", request.getMessage());
            assertEquals(2, request.getChatHistory().size());
            assertEquals(
                    "Hi, my favorite color is green",
                    ((CohereUserMessage) request.getChatHistory().get(0)).getMessage());
            assertEquals(
                    "Hi, nice to meet you",
                    ((CohereChatBotMessage) request.getChatHistory().get(1)).getMessage());
        }
    }

    @Test
    void prepareRequestShouldKeepToolCallHistoryMessagesAddressable() {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var toolExecutionRequest = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("calculateCosineSimilarity")
                .arguments("{\"id1\":0,\"id2\":1}")
                .build();

        try (var model = OciGenAiCohereChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .build()) {

            var request = model.prepareRequest(ChatRequest.builder()
                            .messages(
                                    UserMessage.from("Store embeddings and calculate cosine similarity"),
                                    AiMessage.from(java.util.List.of(toolExecutionRequest)),
                                    ToolExecutionResultMessage.from(toolExecutionRequest, "0"))
                            .build())
                    .build();

            assertEquals("", request.getMessage());
            assertEquals(2, request.getChatHistory().size());

            assertEquals(
                    "Store embeddings and calculate cosine similarity",
                    ((CohereUserMessage) request.getChatHistory().get(0)).getMessage());

            var assistantMessage =
                    (CohereChatBotMessage) request.getChatHistory().get(1);
            assertEquals("", assistantMessage.getMessage());
            assertEquals(1, assistantMessage.getToolCalls().size());
            assertEquals(
                    "calculateCosineSimilarity",
                    assistantMessage.getToolCalls().get(0).getName());

            assertEquals(1, request.getToolResults().size());
            assertEquals(
                    "calculateCosineSimilarity",
                    request.getToolResults().get(0).getCall().getName());
            assertEquals(
                    Map.of("id1", 0, "id2", 1),
                    request.getToolResults().get(0).getCall().getParameters());
            assertFalse(request.getIsForceSingleStep());
        }
    }

    @Test
    void prepareRequestShouldPreserveRepeatedToolCallArgumentsInToolResultsOrder() {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var firstToolExecutionRequest = ToolExecutionRequest.builder()
                .name("storeEmbedding")
                .arguments("{\"input\":\"first\"}")
                .build();
        var secondToolExecutionRequest = ToolExecutionRequest.builder()
                .name("storeEmbedding")
                .arguments("{\"input\":\"second\"}")
                .build();

        try (var model = OciGenAiCohereChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .build()) {

            var request = model.prepareRequest(ChatRequest.builder()
                            .messages(
                                    UserMessage.from("Store both embeddings"),
                                    AiMessage.from(List.of(firstToolExecutionRequest, secondToolExecutionRequest)),
                                    ToolExecutionResultMessage.from(firstToolExecutionRequest, "0"),
                                    ToolExecutionResultMessage.from(secondToolExecutionRequest, "1"))
                            .build())
                    .build();

            assertEquals("", request.getMessage());
            assertEquals(2, request.getChatHistory().size());
            assertEquals(2, request.getToolResults().size());
            assertEquals(
                    Map.of("input", "first"),
                    request.getToolResults().get(0).getCall().getParameters());
            assertEquals(
                    Map.of("input", "second"),
                    request.getToolResults().get(1).getCall().getParameters());
            assertFalse(request.getIsForceSingleStep());
        }
    }

    @Test
    void prepareRequestShouldKeepResolvedToolResultsInHistoryWhenFollowUpUserMessageExists() {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var toolExecutionRequest = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("getWeather")
                .arguments("{\"city\":\"Munich\"}")
                .build();

        try (var model = OciGenAiCohereChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .build()) {

            var request = model.prepareRequest(ChatRequest.builder()
                            .messages(
                                    UserMessage.from("What is the weather in Munich?"),
                                    AiMessage.from(List.of(toolExecutionRequest)),
                                    ToolExecutionResultMessage.from(toolExecutionRequest, "sunny"),
                                    UserMessage.from("What should I wear?"))
                            .build())
                    .build();

            assertEquals("What should I wear?", request.getMessage());
            assertEquals(3, request.getChatHistory().size());
            assertEquals(
                    "What is the weather in Munich?",
                    ((CohereUserMessage) request.getChatHistory().get(0)).getMessage());

            var assistantMessage =
                    (CohereChatBotMessage) request.getChatHistory().get(1);
            assertEquals("", assistantMessage.getMessage());
            assertEquals("getWeather", assistantMessage.getToolCalls().get(0).getName());

            var toolMessage = (CohereToolMessage) request.getChatHistory().get(2);
            assertEquals(1, toolMessage.getToolResults().size());
            assertEquals(
                    "getWeather", toolMessage.getToolResults().get(0).getCall().getName());
            assertEquals(
                    Map.of("city", "Munich"),
                    toolMessage.getToolResults().get(0).getCall().getParameters());

            assertNull(request.getToolResults());
            assertNull(request.getIsForceSingleStep());
        }
    }
}
