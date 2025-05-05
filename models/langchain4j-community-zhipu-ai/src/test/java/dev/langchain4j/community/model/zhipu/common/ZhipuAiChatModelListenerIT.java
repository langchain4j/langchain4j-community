package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiException;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.util.Collections;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return ZhipuAiChatModel.builder()
                .model(modelName())
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .listeners(Collections.singletonList(listener))
                .topP(topP())
                .temperature(temperature())
                .maxToken(maxTokens())
                .build();
    }

    @Override
    protected String modelName() {
        return ChatCompletionModel.GLM_4_FLASH.toString();
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return ZhipuAiChatModel.builder()
                .model("banana")
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .listeners(Collections.singletonList(listener))
                .topP(topP())
                .temperature(temperature())
                .maxToken(maxTokens())
                .maxRetries(1)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return ZhipuAiException.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
