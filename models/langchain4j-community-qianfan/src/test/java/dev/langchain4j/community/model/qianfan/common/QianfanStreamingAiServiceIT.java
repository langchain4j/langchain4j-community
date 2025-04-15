package dev.langchain4j.community.model.qianfan.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return singletonList(QianfanStreamingChatModel.builder()
                .apiKey(System.getenv("QIANFAN_API_KEY"))
                .secretKey(System.getenv("QIANFAN_SECRET_KEY"))
                .modelName("ERNIE-Bot 4.0")
                .temperature(0.01)
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
