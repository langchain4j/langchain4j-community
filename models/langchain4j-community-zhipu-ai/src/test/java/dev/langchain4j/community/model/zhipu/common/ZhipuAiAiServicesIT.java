package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiAiServicesIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return Collections.singletonList(ZhipuAiChatModel.builder()
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .model(ChatCompletionModel.GLM_4_FLASH)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
