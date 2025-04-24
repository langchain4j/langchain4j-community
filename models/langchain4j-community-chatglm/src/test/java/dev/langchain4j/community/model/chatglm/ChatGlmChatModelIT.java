package dev.langchain4j.community.model.chatglm;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CHATGLM_BASE_URL", matches = ".+")
class ChatGlmChatModelIT {

    ChatModel chatModel = ChatGlmChatModel.builder()
            .baseUrl(System.getenv("CHATGLM_BASE_URL"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_answer() {
        UserMessage userMessage = userMessage("你好，请问一下德国的首都是哪里呢？");
        ChatResponse response = chatModel.chat(userMessage);
        assertThat(response.aiMessage().text()).contains("柏林");
    }

    @Test
    void should_generate_answer_from_history() {
        // init history
        List<ChatMessage> messages = new ArrayList<>();

        // given question first time
        UserMessage userMessage = userMessage("你好，请问一下德国的首都是哪里呢？");
        ChatResponse response = chatModel.chat(userMessage);
        assertThat(response.aiMessage().text()).contains("柏林");

        // given question with history
        messages.add(userMessage);
        messages.add(response.aiMessage());

        UserMessage secondUserMessage = userMessage("你能告诉我上个问题我问了你什么呢？请把我的问题原封不动的告诉我");
        messages.add(secondUserMessage);

        ChatResponse secondResponse = chatModel.chat(messages);
        assertThat(secondResponse.aiMessage().text())
                .contains("德国"); // the answer should contain Germany in the First Question
    }
}
