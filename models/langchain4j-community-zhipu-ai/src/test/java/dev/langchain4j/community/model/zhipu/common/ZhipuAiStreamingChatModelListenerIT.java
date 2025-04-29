package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return ZhipuAiStreamingChatModel.builder()
                .model(ChatCompletionModel.GLM_4_FLASH)
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String modelName() {
        return ChatCompletionModel.GLM_4_FLASH.name();
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        // FIXME: Zhipu will return failed AiMessage instead of throwing exception.
        return ZhipuAiStreamingChatModel.builder()
                .model("banana")
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
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
