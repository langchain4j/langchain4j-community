package dev.langchain4j.community.model.zhipu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiStreamingChatModelReasoningIT {

    @Test
    void should_return_thinking() {

        // given
        ZhipuAiStreamingChatModel model = ZhipuAiStreamingChatModel.builder()
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .model(ChatCompletionModel.GLM_4_7)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler spyHandler1 = spy(new TestStreamingChatResponseHandler());
        model.chat(ChatRequest.builder().messages(userMessage1).build(), spyHandler1);

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
                        .build(),
                handler2);

        // then
        AiMessage aiMessage2 = handler2.get().aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
    }
}
