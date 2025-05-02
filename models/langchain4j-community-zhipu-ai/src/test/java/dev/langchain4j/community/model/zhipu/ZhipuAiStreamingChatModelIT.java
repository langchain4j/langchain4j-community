package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.ZhipuAiChatModelIT.multimodalChatMessagesWithImageData;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiStreamingChatModelIT {

    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    private final ZhipuAiStreamingChatModel model = ZhipuAiStreamingChatModel.builder()
            .model(ChatCompletionModel.GLM_4_FLASH)
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
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
    void should_stream_answer() {

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat("Where is the capital of China? Please answer in English", handler);

        ChatResponse response = handler.get();

        assertThat(response.aiMessage().text()).containsIgnoringCase("Beijing");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_sensitive_words_stream_answer() throws Exception {
        CompletableFuture<Throwable> future = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                fail("onNext() must not be called");
            }

            @Override
            public void onError(Throwable error) {
                future.complete(error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                fail("onCompleteResponse() must not be called");
            }
        };

        StreamingChatModel model = ZhipuAiStreamingChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(apiKey + 1)
                .logRequests(true)
                .logResponses(true)
                .build();
        model.chat("this message will fail", handler);

        Throwable throwable = future.get(5, SECONDS);
        assertThat(throwable)
                .isExactlyInstanceOf(ZhipuAiException.class)
                .hasMessageContaining("Authorization Token非法，请确认Authorization Token正确传递。");
    }

    @Test
    void should_execute_a_tool_then_stream_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
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

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

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
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    ToolSpecification currentTime = ToolSpecification.builder()
            .name("currentTime")
            .description("currentTime")
            .build();

    @Test
    void should_execute_get_current_time_tool_and_then_answer() {
        // given
        UserMessage userMessage =
                userMessage("What's the time now? Please give the year and the exact time in seconds.");
        List<ToolSpecification> toolSpecifications = singletonList(currentTime);

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(singletonList(userMessage))
                        .toolSpecifications(toolSpecifications)
                        .build(),
                handler);

        // then
        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("currentTime");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "2024-04-23 12:00:20");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        TestStreamingChatResponseHandler secondHandler = new TestStreamingChatResponseHandler();
        model.chat(messages, secondHandler);

        // then
        ChatResponse secondResponse = secondHandler.get();
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("12:00:20");
        assertThat(secondAiMessage.text()).contains("2024");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_listen_request_and_response() {

        // given
        AtomicReference<ChatRequest> requestReference = new AtomicReference<>();
        AtomicReference<ChatResponse> responseReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.chatRequest());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                responseReference.set(responseContext.chatResponse());
                assertThat(responseContext.chatRequest()).isSameAs(requestReference.get());
                assertThat(responseContext.attributes()).containsEntry("id", "12345");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                fail("onError() must not be called");
            }
        };

        double temperature = 0.7;
        double topP = 0.7;
        int maxTokens = 7;

        StreamingChatModel model = ZhipuAiStreamingChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(apiKey)
                .temperature(temperature)
                .topP(topP)
                .maxToken(maxTokens)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();

        UserMessage userMessage = UserMessage.from("hello");

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("add")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .build())
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(singletonList(userMessage))
                        .toolSpecifications(toolSpecification)
                        .build(),
                handler);
        AiMessage aiMessage = handler.get().aiMessage();

        // then
        ChatRequest request = requestReference.get();
        assertThat(request.parameters().temperature()).isEqualTo(temperature);
        assertThat(request.parameters().topP()).isEqualTo(topP);
        assertThat(request.parameters().maxOutputTokens()).isEqualTo(maxTokens);
        assertThat(request.messages()).containsExactly(userMessage);
        assertThat(request.toolSpecifications()).containsExactly(toolSpecification);

        ChatResponse response = responseReference.get();
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.tokenUsage().outputTokenCount()).isPositive();
        assertThat(response.tokenUsage().totalTokenCount()).isPositive();
        assertThat(response.finishReason()).isNotNull();
        assertThat(response.aiMessage()).isEqualTo(aiMessage);
    }

    @Test
    void should_listen_error() throws Exception {

        AtomicReference<ChatRequest> requestReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();

        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requestReference.set(requestContext.chatRequest());
                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                assertThat(errorContext.chatRequest()).isSameAs(requestReference.get());
                assertThat(errorContext.attributes()).containsEntry("id", "12345");
            }
        };

        StreamingChatModel model = ZhipuAiStreamingChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(apiKey + 1)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();

        String userMessage = "this message will fail";

        CompletableFuture<Throwable> future = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String token) {
                fail("onNext() must not be called");
            }

            @Override
            public void onError(Throwable error) {
                future.complete(error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                fail("onCompleteResponse() must not be called");
            }
        };

        // when
        model.chat(userMessage, handler);
        Throwable throwable = future.get(5, SECONDS);

        // then
        assertThat(errorReference.get()).isInstanceOf(ZhipuAiException.class);
        assertThat(errorReference.get()).isEqualTo(throwable);
        assertThat(errorReference.get()).hasMessageContaining("Authorization Token非法，请确认Authorization Token正确传递。");
    }

    @Test
    public void should_send_multimodal_image_data_and_receive_response() {
        StreamingChatModel model = ZhipuAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .model(ChatCompletionModel.GLM_4V)
                .build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(multimodalChatMessagesWithImageData(), handler);
        ChatResponse response = handler.get();

        assertThat(response.aiMessage().text()).containsIgnoringCase("parrot");
        assertThat(response.aiMessage().text()).endsWith("That's all!");
    }
}
