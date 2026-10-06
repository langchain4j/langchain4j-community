package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN3_8_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenAiServicesIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        // qwen3.8-max calls AUTO tools reliably with thinking mode enabled
        // (qwen3.8-flash intermittently answers directly instead of calling the tool)
        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .temperature(0.0d)
                .enableSanitizeMessages(false)
                .build();

        return singletonList(QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN3_8_MAX)
                .defaultRequestParameters(parameters)
                .build());
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return QwenTokenUsage.class;
    }
}
