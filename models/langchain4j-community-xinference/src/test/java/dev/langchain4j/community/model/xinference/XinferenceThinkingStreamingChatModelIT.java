package dev.langchain4j.community.model.xinference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        streamingChatModel.chat("委内瑞拉的首都是哪儿?", handler);

        // then
        AiMessage aiMessage = handler.get().aiMessage();

        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).isNotBlank();
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

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        streamingChatModel.chat("委内瑞拉的首都是在哪儿?", handler);

        // then
        AiMessage aiMessage = handler.get().aiMessage();

        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.text()).isNotBlank();
    }
}
