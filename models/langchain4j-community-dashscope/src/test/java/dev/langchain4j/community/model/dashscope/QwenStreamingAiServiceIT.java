package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenStreamingAiServiceIT extends AbstractStreamingAiServiceIT {
    @Override
    protected List<StreamingChatLanguageModel> models() {
        return singletonList(QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN_MAX)
                .temperature(0.0f)
                .build());
    }
}
