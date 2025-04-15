package dev.langchain4j.community.model.qianfan.common;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanStreamingChatModelIT extends AbstractStreamingChatModelIT {

    // see your api key and secret key here:
    // https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private final String apiKey = System.getenv("QIANFAN_API_KEY");
    private final String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    QianfanStreamingChatModel model = QianfanStreamingChatModel.builder()
            .modelName("ERNIE-Bot 4.0")
            .temperature(0.7)
            .topP(1.0)
            .apiKey(apiKey)
            .secretKey(secretKey)
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
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_valid_json() {

        // given
        String userMessage = "Return JSON with  fields: name of Klaus. ";
        // nudging it to say something additionally to json
        QianfanStreamingChatModel model = QianfanStreamingChatModel.builder()
                .modelName("ERNIE-Bot 4.0")
                .temperature(0.7)
                .topP(1.0)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .responseFormat("json_object")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        model.chat(userMessage, handler);

        ChatResponse response = handler.get();
        String json = response.aiMessage().text();
        // then
        assertThat(json).contains("\"name\": \"Klaus\"");
        assertThat(response.aiMessage().text()).isEqualTo(json);
    }

    @Override
    protected List<StreamingChatModel> models() {
        return singletonList(model);
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false; // TODO
    }

    @Override
    protected boolean supportsTools() {
        return false; // TODO
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false; // TODO
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithSingleTool() {
        return false; // TODO
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // TODO
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // TODO
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // TODO
    }

    @Override
    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return false; // TODO
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // TODO
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false; // TODO
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // TODO
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return false; // TODO
    }
}
