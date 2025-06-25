package dev.langchain4j.community.model.xinference.common;

import static dev.langchain4j.community.model.xinference.AbstractInferenceChatModelInfrastructure.LOCAL_IMAGE;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.XINFERENCE_BASE_URL;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.XINFERENCE_IMAGE;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.resolve;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.xinference.AbstractInferenceChatModelInfrastructure;
import dev.langchain4j.community.model.xinference.XinferenceContainer;
import dev.langchain4j.community.model.xinference.XinferenceStreamingChatModel;
import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class XinferenceStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    static XinferenceContainer xinference;

    @BeforeAll
    static void beforeAll() {
        if (isNullOrEmpty(XINFERENCE_BASE_URL)) {
            xinference = new XinferenceContainer(resolve(XINFERENCE_IMAGE, LOCAL_IMAGE))
                    .withModel(AbstractInferenceChatModelInfrastructure.modelName());
            xinference.start();
        }
    }

    @AfterAll
    static void afterAll() {
        if (xinference != null) {
            xinference.stop();
        }
    }

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
