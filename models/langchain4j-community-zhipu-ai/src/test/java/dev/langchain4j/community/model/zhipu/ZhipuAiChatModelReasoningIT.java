package dev.langchain4j.community.model.zhipu;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiChatModelReasoningIT {

    @Test
    void should_return_thinking() {

        // given
        ChatModel model = ZhipuAiChatModel.builder()
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .model(ChatCompletionModel.GLM_4_7)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage1 = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse chatResponse1 =
                model.chat(ChatRequest.builder().messages(userMessage1).build());

        // then
        AiMessage aiMessage1 = chatResponse1.aiMessage();
        assertThat(aiMessage1.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage1.thinking()).isNotBlank();

        // given
        UserMessage userMessage2 = UserMessage.from("What is the capital of France?");

        // when
        ChatResponse chatResponse2 = model.chat(userMessage1, aiMessage1, userMessage2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).containsIgnoringCase("Paris");
        assertThat(aiMessage2.thinking()).isNotBlank();
    }
}
