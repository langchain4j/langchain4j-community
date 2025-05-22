package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiChatModelIT {
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    ZhipuAiChatModel chatModel = ZhipuAiChatModel.builder()
            .model(ChatCompletionModel.GLM_4_FLASH)
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
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
    void should_chat_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("中国首都在哪里");

        // when
        ChatResponse response = chatModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("北京");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_sensitive_words_answer() {
        ZhipuAiChatModel model = ZhipuAiChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(apiKey + 1)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .build();
        // given
        UserMessage userMessage = userMessage("this message will fail");

        // when
        assertThatThrownBy(() -> model.chat(userMessage))
                .isExactlyInstanceOf(ZhipuAiException.class)
                .hasMessageContaining("Authorization Token非法，请确认Authorization Token正确传递。");
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(singletonList(userMessage))
                .toolSpecifications(toolSpecifications)
                .build());

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
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
        ChatResponse secondResponse = chatModel.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
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
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(singletonList(userMessage))
                .toolSpecifications(toolSpecifications)
                .build());

        // then
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
        ChatResponse secondResponse = chatModel.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).containsAnyOf("12:00:20", "2024");
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

        ZhipuAiChatModel model = ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model(ChatCompletionModel.GLM_4_FLASH)
                .topP(topP)
                .maxToken(maxTokens)
                .temperature(temperature)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
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
        AiMessage aiMessage = model.chat(ChatRequest.builder()
                        .messages(singletonList(userMessage))
                        .toolSpecifications(toolSpecification)
                        .build())
                .aiMessage();

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
    void should_listen_error() {

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

        ZhipuAiChatModel model = ZhipuAiChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(apiKey + 1)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .listeners(singletonList(listener))
                .build();

        String userMessage = "this message will fail";
        assertThatThrownBy(() -> model.chat(userMessage))
                .isExactlyInstanceOf(ZhipuAiException.class)
                .hasMessageContaining("Authorization Token非法，请确认Authorization Token正确传递。");

        // then
        Throwable throwable = errorReference.get();
        assertThat(throwable).isInstanceOf(ZhipuAiException.class);
        assertThat(throwable).hasMessageContaining("Authorization Token非法，请确认Authorization Token正确传递。");
    }

    @Test
    public void should_send_multimodal_image_data_and_receive_response() {
        ChatModel model = ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model(ChatCompletionModel.GLM_4V)
                .build();

        ChatResponse response = model.chat(multimodalChatMessagesWithImageData());

        assertThat(response.aiMessage().text()).containsIgnoringCase("parrot");
        assertThat(response.aiMessage().text()).endsWith("That's all!");
    }

    public static List<ChatMessage> multimodalChatMessagesWithImageData() {
        Image image = Image.builder().base64Data(multimodalImageData()).build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent =
                TextContent.from("What animal is in the picture? When you're done, end with \"That's all!\".");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }

    public static String multimodalImageData() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = ZhipuAiChatModelIT.class.getResourceAsStream("/parrot.jpg")) {
            assertThat(in).isNotNull();
            byte[] data = new byte[512];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
        } catch (IOException e) {
            fail("", e.getMessage());
        }

        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }
}
