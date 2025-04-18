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
import dev.langchain4j.community.model.qianfan.QianfanChatModel;
import dev.langchain4j.community.model.qianfan.client.QianfanApiException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanChatModelIT extends AbstractChatModelIT {

    // see your api key and secret key here:
    // https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private final String apiKey = System.getenv("QIANFAN_API_KEY");
    private final String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    QianfanChatModel model = QianfanChatModel.builder()
            .modelName("ERNIE-Bot 4.0")
            .temperature(0.7)
            .topP(1.0)
            .maxRetries(1)
            .apiKey(apiKey)
            .secretKey(secretKey)
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
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("中国首都在哪里");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).contains("北京");

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        ChatResponse response = model.chat(ChatRequest.builder()
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
        ChatResponse secondResponse = model.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_valid_json() {
        QianfanChatModel model = QianfanChatModel.builder()
                .modelName("ERNIE-Bot 4.0")
                .temperature(0.7)
                .topP(1.0)
                .maxRetries(1)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .responseFormat("json_object")
                .build();

        // given
        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. ";
        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        assertThat(model.chat(userMessage)).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void should_generate_answer_with_system_message() {

        // given
        UserMessage userMessage = userMessage("Where is the capital of China");

        SystemMessage systemMessage = SystemMessage.from("Please add the word hello before each answer");

        // when
        ChatResponse response = model.chat(userMessage, systemMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("hello");
    }

    @Test
    void should_generate_answer_with_even_number_of_messages() {

        // Assume this history message has been removed because of the chat memory's sliding window mechanism.
        UserMessage historyMessage = userMessage("Where is the capital of China");

        AiMessage aiMessage = AiMessage.aiMessage("Hello, The capital of China is Beijing.");

        UserMessage userMessage = userMessage("What are the districts of Beijing?");

        SystemMessage systemMessage = SystemMessage.from("Please add the word hello before each answer");

        // length of message is even excluding system message.
        ChatResponse response = model.chat(aiMessage, userMessage, systemMessage);

        assertThat(response.aiMessage().text()).containsIgnoringCase("hello");
    }

    @Test
    void should_throw_exception_when_api_has_error_code() {
        ChatModel chatModel = QianfanChatModel.builder()
                // Any other models that have not been activated yet.
                .modelName("EB-turbo-AppBuilder")
                .apiKey(apiKey)
                .secretKey(secretKey)
                .build();

        try {
            chatModel.chat(userMessage("Where is the capital of China"));
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(QianfanApiException.class);
        }
    }

    @Override
    protected List<ChatModel> models() {
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
