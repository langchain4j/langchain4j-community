package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_TURBO;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenStreamingChatModelReasoningIT {

    @Test
    void should_return_thinking() {

        // given
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN_TURBO)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(
                ChatRequest.builder()
                        .messages(userMessage1)
                        .parameters(QwenChatRequestParameters.builder()
                                .enableThinking(true)
                                .build())
                        .build(),
                spyHandler1);

        // then
        AiMessage aiMessage1 = spyHandler1.get().aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).containsIgnoringCase("Berlin").isEqualTo(spyHandler1.getThinking());

        InOrder inOrder1 = inOrder(spyHandler1);
        inOrder1.verify(spyHandler1).get();
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialThinking(any());
        inOrder1.verify(spyHandler1, atLeastOnce()).onPartialResponse(any());
        inOrder1.verify(spyHandler1).onCompleteResponse(any());
        inOrder1.verify(spyHandler1).getThinking();
        inOrder1.verifyNoMoreInteractions();
        verifyNoMoreInteractions(spyHandler1);

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(userMessage1, aiMessage1, userMessage2)
                        .parameters(QwenChatRequestParameters.builder()
                                .enableThinking(true)
                                .build())
                        .build(),
                handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_NOT_return_thinking(Boolean returnThinking) {

        // given
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(QWEN_TURBO)
                .build();

        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(userMessage))
                        .parameters(QwenChatRequestParameters.builder()
                                .enableThinking(returnThinking)
                                .build())
                        .build(),
                spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();

        verify(spyHandler, never()).onPartialThinking(any());
    }
}
