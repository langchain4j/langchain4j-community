package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN3_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenAiServicesWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .temperature(0.0d)
                .enableSanitizeMessages(false)
                .build();

        return singletonList(QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN3_MAX)
                .defaultRequestParameters(parameters)
                .build());
    }

    @ParameterizedTest
    @MethodSource({"models"})
    @Override
    protected void should_execute_immediate_tool_with_primitive_parameters(ChatModel chatModel) {
        // For common problems, the model is less likely to require the use of tools. Force it.
        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .temperature(0.0d)
                .enableSanitizeMessages(false)
                .toolChoice(ToolChoice.REQUIRED)
                .build();

        ChatModel qwenModel = QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN3_MAX)
                .defaultRequestParameters(parameters)
                .build();

        super.should_execute_immediate_tool_with_primitive_parameters(qwenModel);
    }
}
