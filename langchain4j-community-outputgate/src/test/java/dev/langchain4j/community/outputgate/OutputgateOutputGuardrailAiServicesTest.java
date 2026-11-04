package dev.langchain4j.community.outputgate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutputgateOutputGuardrailAiServicesTest {

    @Test
    void should_reprompt_with_violations_and_return_the_corrected_output() {
        ScriptedChatModel model = new ScriptedChatModel(Contracts.WRONG_TOTAL, Contracts.VALID);
        SurveyExtractor extractor = AiServices.builder(SurveyExtractor.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .outputGuardrails(new OutputgateOutputGuardrail(Contracts.survey()))
                .build();

        String output = extractor.extract("Of 120 people asked, 70 said yes and 50 said no.");

        assertThat(output).isEqualTo(Contracts.VALID);
        assertThat(model.requests).hasSize(2);
        assertThat(lastUserMessage(model.requests.get(1)))
                .contains("total_responses: sum of options[*].count is 120 but total_responses is 130");
    }

    @Test
    void should_return_a_typed_result_from_fenced_json() {
        ScriptedChatModel model = new ScriptedChatModel("```json\n" + Contracts.VALID + "\n```");
        TypedSurveyExtractor extractor = AiServices.builder(TypedSurveyExtractor.class)
                .chatModel(model)
                .outputGuardrails(new OutputgateOutputGuardrail(Contracts.survey()))
                .build();

        Survey survey = extractor.extract("Of 120 people asked, 70 said yes and 50 said no.");

        assertThat(survey.total_responses()).isEqualTo(120);
        assertThat(survey.options()).extracting(Option::count).containsExactly(70, 50);
    }

    @Test
    void should_throw_once_reprompts_are_used_up() {
        ScriptedChatModel model =
                new ScriptedChatModel(Contracts.WRONG_TOTAL, Contracts.WRONG_TOTAL, Contracts.WRONG_TOTAL);
        SurveyExtractor extractor = AiServices.builder(SurveyExtractor.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().maxRetries(2).build())
                .outputGuardrails(new OutputgateOutputGuardrail(Contracts.survey()))
                .build();

        assertThatThrownBy(() -> extractor.extract("Of 120 people asked, 70 said yes and 50 said no."))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("total_responses");
        assertThat(model.requests).hasSize(2);
    }

    private static String lastUserMessage(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        throw new IllegalStateException("no user message");
    }

    interface SurveyExtractor {

        @dev.langchain4j.service.UserMessage("Summarize this survey as JSON: {{it}}")
        String extract(String text);
    }

    interface TypedSurveyExtractor {

        @dev.langchain4j.service.UserMessage("Summarize this survey as JSON: {{it}}")
        Survey extract(String text);
    }

    record Survey(String question, int total_responses, List<Option> options) {}

    record Option(String label, int count) {}

    static class ScriptedChatModel implements ChatModel {

        private final Deque<String> replies;
        final List<ChatRequest> requests = new ArrayList<>();

        ScriptedChatModel(String... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(replies.removeFirst()))
                    .build();
        }
    }
}
