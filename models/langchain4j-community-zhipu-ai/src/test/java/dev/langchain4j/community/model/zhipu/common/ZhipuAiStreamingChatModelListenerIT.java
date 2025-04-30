package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiException;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.util.Collections;
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
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return ZhipuAiStreamingChatModel.builder()
                .model("banana")
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .logRequests(true)
                .logResponses(true)
                .listeners(Collections.singletonList(listener))
                .topP(topP())
                .temperature(temperature())
                .maxToken(maxTokens())
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
