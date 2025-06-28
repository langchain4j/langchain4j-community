package dev.langchain4j.community.model.qianfan.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
public class QianfanStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    private final String apiKey = System.getenv("QIANFAN_API_KEY");
    private final String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return QianfanStreamingChatModel.builder()
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
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
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return QianfanStreamingChatModel.builder()
                .endpoint("banana")
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return IllegalStateException.class;
    }
}
