package dev.langchain4j.community.model.xinference.common;

import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.xinference.AbstractInferenceChatModelInfrastructure;
import dev.langchain4j.community.model.xinference.XinferenceStreamingChatModel;
import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

class XinferenceStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return XinferenceStreamingChatModel.builder()
                .baseUrl(AbstractInferenceChatModelInfrastructure.baseUrl())
                .apiKey(AbstractInferenceChatModelInfrastructure.apiKey())
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxTokens(maxTokens())
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    /**
     * Streaming support for tool calls is available only when using Qwen models with vLLM backend or
     * GLM4-chat models without vLLM backend.
     *
     * @return
     */
    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected String modelName() {
        return AbstractInferenceChatModelInfrastructure.modelName();
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return XinferenceStreamingChatModel.builder()
                .baseUrl(AbstractInferenceChatModelInfrastructure.baseUrl())
                .modelName("llama3.1")
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return XinferenceHttpException.class;
    }
}
