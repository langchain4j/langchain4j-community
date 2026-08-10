package dev.langchain4j.community.model.xinference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

class XinferenceThinkingModelIT extends AbstractXinferenceThinkingModelInfrastructure {

    @Test
    void should_include_thinking_content_when_thinking_flag_is_enabled() {

        // given
        ChatModel chatModel = XinferenceChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(modelName())
                .enableThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        AiMessage aiMessage = chatModel.chat(UserMessage.from("西班牙的首都是在哪儿")).aiMessage();

        // then
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    void should_NOT_include_thinking_content_when_thinking_flag_is_disabled_forcefully() {

        // given
        ChatModel chatModel = XinferenceChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(modelName())
                .enableThinking(false)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        AiMessage aiMessage = chatModel.chat(UserMessage.from("西班牙的首都是在哪儿")).aiMessage();

        // then
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.text()).isNotBlank();
    }
}
