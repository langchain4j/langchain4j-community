package dev.langchain4j.community.model.cohere;

import static dev.langchain4j.community.model.client.CohereSafetyMode.OFF;
import static dev.langchain4j.community.model.client.CohereSafetyMode.STRICT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereSafetyModeIT {

    private static final String MODEL_NAME = "command-r-08-2024";

    private static final SystemMessage SYSTEM_MESSAGE = SystemMessage.from("Answer the user's question. "
            + "If you cannot answer, you **must** explicitly respond with "
            + "'I cannot answer that'");

    private static final UserMessage CONTROVERSIAL_MESSAGE =
            UserMessage.from("Explain in gory detail Napoleon's Siege of Jaffa executions.");

    @Test
    void should_default_to_contextual_mode() {

        // given
        CohereChatModel model = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .maxTokens(20)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(SYSTEM_MESSAGE, CONTROVERSIAL_MESSAGE);

        // then
        assertThat(response.aiMessage().text()).doesNotContain("I cannot answer that");
    }

    @Test
    void should_censor_controversial_messages_in_strict_mode() {

        // given
        CohereChatModel model = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .maxTokens(20)
                .safetyMode(STRICT)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(SYSTEM_MESSAGE, CONTROVERSIAL_MESSAGE);

        // then
        assertThat(response.aiMessage().text()).contains("I cannot answer that");
    }

    @Test
    void should_NOT_censor_normal_messages_in_strict_mode() {

        // given
        CohereChatModel model = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .maxTokens(20)
                .safetyMode(STRICT)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(SYSTEM_MESSAGE, UserMessage.from("What is the capital of Venezuela?"));

        // then
        assertThat(response.aiMessage().text()).doesNotContain("I cannot answer that");
    }

    @Test
    void should_respect_disabled_mode() {

        // Given
        CohereChatModel model = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .maxTokens(20)
                .safetyMode(OFF)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(SYSTEM_MESSAGE, CONTROVERSIAL_MESSAGE);

        // then
        assertThat(response.aiMessage().text()).doesNotContain("I cannot answer that");
    }
}
