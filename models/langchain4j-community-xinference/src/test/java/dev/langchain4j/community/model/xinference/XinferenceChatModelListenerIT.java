package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "XINFERENCE_BASE_URL", matches = ".+")
class XinferenceChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return XinferenceChatModel.builder()
                .baseUrl(AbstractModelInfrastructure.XINFERENCE_BASE_URL)
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
        return AbstractModelInfrastructure.CHAT_MODEL_NAME;
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return XinferenceChatModel.builder()
                .apiKey("banana")
                .maxRetries(1)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return XinferenceHttpException.class;
    }
}
