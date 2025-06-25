package dev.langchain4j.community.model.xinference.common;

import static dev.langchain4j.community.model.xinference.AbstractInferenceChatModelInfrastructure.LOCAL_IMAGE;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.XINFERENCE_BASE_URL;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.XINFERENCE_IMAGE;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.resolve;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.xinference.AbstractInferenceChatModelInfrastructure;
import dev.langchain4j.community.model.xinference.XinferenceChatModel;
import dev.langchain4j.community.model.xinference.XinferenceContainer;
import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class XinferenceChatModelListenerIT extends AbstractChatModelListenerIT {

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
        xinference.stop();
    }

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return XinferenceChatModel.builder()
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

    @Override
    protected String modelName() {
        return AbstractInferenceChatModelInfrastructure.modelName();
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return XinferenceChatModel.builder()
                .baseUrl(AbstractInferenceChatModelInfrastructure.baseUrl())
                .modelName("llama3.1")
                .maxRetries(1)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return XinferenceHttpException.class;
    }
}
