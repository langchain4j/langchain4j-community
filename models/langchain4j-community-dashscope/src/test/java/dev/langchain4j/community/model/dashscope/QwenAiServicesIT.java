package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN3_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenAiServicesIT extends AbstractAiServiceIT {
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
}
