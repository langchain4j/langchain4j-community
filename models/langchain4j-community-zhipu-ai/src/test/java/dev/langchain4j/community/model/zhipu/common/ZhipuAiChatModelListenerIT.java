package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return ZhipuAiChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .build();
    }

    @Override
    protected String modelName() {
        return ChatCompletionModel.GLM_4_FLASH.name();
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        // FIXME: Zhipu will return failed AiMessage instead of throwing exception.
        return ZhipuAiChatModel.builder()
                .model("banana")
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return ModelNotFoundException.class;
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
