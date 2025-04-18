package dev.langchain4j.community.model.zhipu;

import static java.time.Duration.ofSeconds;

import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiAiServicesWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return Collections.singletonList(ZhipuAiChatModel.builder()
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .model(ChatCompletionModel.GLM_4_AIR)
                .temperature(0.0)
                .callTimeout(ofSeconds(60))
                .connectTimeout(ofSeconds(60))
                .readTimeout(ofSeconds(60))
                .writeTimeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
