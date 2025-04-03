package dev.langchain4j.community.model.novitaai;


import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.community.model.novitaai.NovitaAiChatModelName.DEEPSEEK_V3;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.*;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "NOVITA_AI_API_KEY", matches = ".*")
class NovitaAiStreamingChatModelIT {

    static NovitaAiStreamingChatModel chatModel;

    @BeforeAll
    static void initializeModel() {
        chatModel = NovitaAiStreamingChatModel.builder()
                .modelName(DEEPSEEK_V3)
                .apiKey(System.getenv("NOVITA_AI_API_KEY"))
                .logResponses(true)
                .build();
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        chatModel.chat(List.of(userMessage), handler);

        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_length() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        chatModel.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Lima");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop_with_multiple_messages() {
        // given
        UserMessage userMessage1 = userMessage("What is the capital of China?");
        UserMessage userMessage2 = userMessage("And what is the capital of France?");
        UserMessage userMessage3 = userMessage("And what is the capital of America?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        chatModel.chat(List.of(userMessage1, userMessage2, userMessage3), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Beijing");
        assertThat(response.aiMessage().text()).containsIgnoringCase("Paris");
        assertThat(response.aiMessage().text()).containsIgnoringCase("Washington");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
