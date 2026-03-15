package dev.langchain4j.community.model.cohere.common;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static java.util.Collections.singletonList;

class CohereChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return CohereChatModel.builder()
                .authToken(System.getenv("CO_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .maxOutputTokens(maxTokens())
                .topP(topP())
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "command-r7b-12-2024";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return CohereChatModel.builder()
                .authToken("mondongo")
                .modelName(modelName())
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return dev.langchain4j.exception.AuthenticationException.class;
    }
}
