package dev.langchain4j.community.model.cohere.common;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereAiServiceIT extends AbstractAiServiceIT {

    private static final ChatModel COHERE_CHAT_MODEL = CohereChatModel.builder()
            .authToken(System.getenv("CO_API_KEY"))
            .modelName("command-r7b-12-2024")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() { return List.of(COHERE_CHAT_MODEL); }
}
