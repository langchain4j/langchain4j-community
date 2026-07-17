package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.query.transformer.RepeatingQueryTransformer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class PromptRepetitionSmokeIT {

    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    private static final String COUNT_TOKEN = "PROMPT_REPETITION_SMOKE_TOKEN";

    private final OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .projectId(System.getenv("OPENAI_PROJECT_ID"))
            .modelName(System.getenv().getOrDefault("OPENAI_MODEL", DEFAULT_OPENAI_MODEL))
            .temperature(0.0)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(1)
            .build();

    interface NonRagAssistant {

        @SystemMessage("Count how many times the exact token 'PROMPT_REPETITION_SMOKE_TOKEN' appears in the user "
                + "message text. Return only the integer number.")
        String countOccurrences(String userMessage);
    }

    interface RagAssistant {

        @SystemMessage("Use retrieved context when present and answer in one short sentence.")
        String answer(String userMessage);
    }

    @Test
    void should_apply_prompt_repetition_via_input_guardrail_with_real_model() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");
        NonRagAssistant assistant = AiServices.builder(NonRagAssistant.class)
                .chatModel(chatModel)
                .inputGuardrails(new PromptRepeatingInputGuardrail(policy))
                .build();

        // when
        String response = assistant.countOccurrences(COUNT_TOKEN);

        // then
        assertThat(firstInteger(response)).isEqualTo(2);
    }

    @Test
    void should_apply_repeating_query_transformer_in_rag_pipeline_with_real_model() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");
        AtomicReference<String> observedQueryText = new AtomicReference<>();
        ContentRetriever retriever = query -> {
            observedQueryText.set(query.text());
            return List.of(Content.from("Retrieved context for query: " + query.text()));
        };

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(new RepeatingQueryTransformer(policy))
                .contentRetriever(retriever)
                .build();
        RagAssistant assistant = AiServices.builder(RagAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .build();
        String inputQuery = "smoke query for repeating transformer";

        // when
        String answer = assistant.answer(inputQuery);

        // then
        assertThat(observedQueryText).hasValue(inputQuery + "\n" + inputQuery);
        assertThat(answer).isNotBlank();
    }

    private static int firstInteger(String text) {
        Matcher matcher = Pattern.compile("-?\\d+").matcher(text == null ? "" : text);
        if (!matcher.find()) {
            throw new IllegalStateException("No integer found in model response: " + text);
        }
        return Integer.parseInt(matcher.group());
    }
}
