package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "XINFERENCE_BASE_URL", matches = ".+")
class XinferenceStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return XinferenceStreamingChatModel.builder()
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
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return XinferenceStreamingChatModel.builder()
                .apiKey("banana")
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
