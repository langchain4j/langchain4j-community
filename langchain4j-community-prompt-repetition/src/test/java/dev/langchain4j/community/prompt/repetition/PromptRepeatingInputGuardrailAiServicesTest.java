package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PromptRepeatingInputGuardrailAiServicesTest {

    private static final ImageContent IMAGE_CONTENT = ImageContent.from(
            Image.builder().url("https://example.com/image.png").build());

    @Test
    void should_skip_rewrite_for_multimodal_ai_services_user_message() {

        AtomicReference<UserMessage> userMessageSeenByChatModel = new AtomicReference<>();
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");

        VisionAssistant assistant = AiServices.builder(VisionAssistant.class)
                .chatModel(new RecordingChatModel(userMessageSeenByChatModel))
                .inputGuardrails(new PromptRepeatingInputGuardrail(policy))
                .build();

        assistant.describe(IMAGE_CONTENT);

        assertThat(userMessageSeenByChatModel.get()).isNotNull();
        assertThat(userMessageSeenByChatModel.get().contents())
                .containsExactly(TextContent.from("Describe this image"), IMAGE_CONTENT);
        assertThat(userMessageSeenByChatModel.get().hasSingleText()).isFalse();
    }

    interface VisionAssistant {

        @dev.langchain4j.service.UserMessage("Describe this image")
        String describe(@dev.langchain4j.service.UserMessage ImageContent image);
    }

    static class RecordingChatModel implements ChatModel {

        private final AtomicReference<UserMessage> observedUserMessage;

        RecordingChatModel(AtomicReference<UserMessage> observedUserMessage) {
            this.observedUserMessage = observedUserMessage;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            observedUserMessage.set(chatRequest.messages().stream()
                    .filter(message -> message.type() == ChatMessageType.USER)
                    .map(UserMessage.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No user message found")));
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }
}
