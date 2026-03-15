package dev.langchain4j.community.model.cohere;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CohereChatModelThinkingIT {

    private static final Integer MAX_THINKING_TOKENS = 1024;


    @Test
    void should_return_thinking() {

        // given
        ChatModel chatModel = CohereChatModel.builder()
                .authToken(System.getenv("CO_API_KEY"))
                .modelName("command-a-reasoning-08-2025")
                .thinkingType("enabled")
                .thinkingTokenBudget(MAX_THINKING_TOKENS)
                .build();

        // when
        ChatResponse response = chatModel.chat(
                UserMessage.from("What is the Capital of Venezuela?"));

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Caracas");
        assertThat(aiMessage.thinking()).isNotBlank();
    }

    @Test
    void should_allow_disabling_thinking() {

        // given
        ChatModel chatModel = CohereChatModel.builder()
                .authToken(System.getenv("CO_API_KEY"))
                .modelName("command-a-reasoning-08-2025")
                .thinkingType("disabled")
                .thinkingTokenBudget(MAX_THINKING_TOKENS)
                .build();

        // when
        ChatResponse response = chatModel.chat(
                UserMessage.from("What is the Capital of Venezuela?"));


        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Caracas");
        assertThat(aiMessage.thinking()).isBlank();
    }

}
