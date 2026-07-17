package dev.langchain4j.community.model.cohere;

import static dev.langchain4j.community.model.client.CohereThinkingType.DISABLED;
import static dev.langchain4j.community.model.client.CohereThinkingType.ENABLED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereChatModelThinkingIT {

    private static final Integer MAX_THINKING_TOKENS = 20;

    @Test
    void should_return_thinking() {

        // given
        ChatModel chatModel = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName("command-a-reasoning-08-2025")
                .thinkingType(ENABLED)
                .thinkingTokenBudget(MAX_THINKING_TOKENS)
                .maxTokens(100)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = chatModel.chat(UserMessage.from("What is the Capital of Venezuela?"));

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Caracas");
        assertThat(aiMessage.thinking()).isNotBlank();
    }

    @Test
    void should_allow_disabling_thinking() {

        // given
        ChatModel chatModel = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName("command-a-reasoning-08-2025")
                .thinkingType(DISABLED)
                .thinkingTokenBudget(MAX_THINKING_TOKENS)
                .maxTokens(20)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = chatModel.chat(UserMessage.from("What is the Capital of Venezuela?"));

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Caracas");
        assertThat(aiMessage.thinking()).isBlank();
    }
}
