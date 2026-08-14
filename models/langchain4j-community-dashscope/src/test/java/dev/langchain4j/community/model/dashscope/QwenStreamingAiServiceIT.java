package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN3_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenStreamingAiServiceIT extends AbstractStreamingAiServiceIT {
    @Override
    protected List<StreamingChatModel> models() {
        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .temperature(0.0d)
                .enableSanitizeMessages(false)
                .build();

        return singletonList(QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN3_MAX)
                .defaultRequestParameters(parameters)
                .build());
    }

    @ParameterizedTest
    @MethodSource({"models"})
    @Override
    protected void should_keep_memory_consistent_when_streaming_using_immediate_tool(StreamingChatModel model) {
        // For common problems, the model is less likely to require the use of tools. Force it.
        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .temperature(0.0d)
                .enableSanitizeMessages(false)
                .toolChoice(ToolChoice.REQUIRED)
                .build();

        StreamingChatModel qwenModel = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN3_MAX)
                .defaultRequestParameters(parameters)
                .build();

        super.should_keep_memory_consistent_when_streaming_using_immediate_tool(qwenModel);
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return QwenChatResponseMetadata.class;
    }
}
