package dev.langchain4j.community.model.xinference;

import static java.util.Collections.singletonList;

import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

class XinferenceChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
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
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
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
