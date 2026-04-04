package dev.langchain4j.community.model.cohere.common;

import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    private static final StreamingChatModel DEFAULT_STREAMING_CHAT_MODEL = CohereStreamingChatModel.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("command-r7b-12-2024")
            .temperature(0.0)
            .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(DEFAULT_STREAMING_CHAT_MODEL);
    }
}
