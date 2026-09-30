package dev.langchain4j.community.model.xinference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

class XinferenceThinkingStreamingChatModelIT extends AbstractXinferenceThinkingModelInfrastructure {

    @Test
    void should_include_thinking_content_when_thinking_flag_is_enabled() {

        // given
        XinferenceStreamingChatModel streamingChatModel = XinferenceStreamingChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(modelName())
                .enableThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());

        // when
        streamingChatModel.chat("委内瑞拉的首都是哪儿?", spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();

        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).isNotBlank();

        verify(spyHandler, atLeastOnce()).onPartialThinking(any());
        assertThat(spyHandler.getThinking()).isEqualTo(aiMessage.thinking());
    }

    @Test
    void should_NOT_include_thinking_content_when_thinking_flag_is_explicitly_disabled() {

        // given
        XinferenceStreamingChatModel streamingChatModel = XinferenceStreamingChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(modelName())
                .enableThinking(false)
                .logRequests(true)
                .logResponses(true)
                .build();

        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());

        // when
        streamingChatModel.chat("委内瑞拉的首都是在哪儿?", spyHandler);

        // then
        AiMessage aiMessage = spyHandler.get().aiMessage();

        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.text()).isNotBlank();

        verify(spyHandler, never()).onPartialThinking(any());
    }
}
