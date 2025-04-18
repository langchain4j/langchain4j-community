package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return QwenChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName())
                .temperature(temperature().floatValue())
                .topP(topP())
                .maxTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return QWEN_MAX;
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return QwenChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return com.alibaba.dashscope.exception.ApiException.class;
    }
}
