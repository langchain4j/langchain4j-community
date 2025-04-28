package dev.langchain4j.community.model.qianfan.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.qianfan.QianfanChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanAiServicesIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatModel> models() {
        return singletonList(QianfanChatModel.builder()
                .apiKey(System.getenv("QIANFAN_API_KEY"))
                .secretKey(System.getenv("QIANFAN_SECRET_KEY"))
                .modelName("ERNIE-Bot 4.0")
                .temperature(0.01)
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
