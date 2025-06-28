package dev.langchain4j.community.model.qianfan.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.qianfan.QianfanChatModel;
import dev.langchain4j.community.model.qianfan.client.QianfanApiException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
public class QianfanChatModelListenerIT extends AbstractChatModelListenerIT {

    private final String apiKey = System.getenv("QIANFAN_API_KEY");
    private final String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return QianfanChatModel.builder()
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .maxRetries(1)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "ERNIE-Tiny-8K";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return QianfanChatModel.builder()
                .endpoint("banana")
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .maxRetries(1)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return QianfanApiException.class;
    }
}
