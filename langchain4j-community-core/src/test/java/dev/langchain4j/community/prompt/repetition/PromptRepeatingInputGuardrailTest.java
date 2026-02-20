package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.rag.AugmentationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptRepeatingInputGuardrailTest {

    @Test
    void should_repeat_single_text_input() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        InputGuardrailRequest request = request(UserMessage.from("Hello"), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.text()).isEqualTo("Hello\nHello");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.successfulText()).isEqualTo("Hello\nHello");
    }

    @Test
    void should_skip_non_text_input() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        InputGuardrailRequest request =
                request(UserMessage.from(ImageContent.from("https://example.com/image.png")), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_NON_TEXT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_skip_when_rag_is_detected_by_default() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        InputGuardrailRequest request = request(UserMessage.from("How old is he?"), true);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_RAG_DETECTED);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_repeat_when_rag_is_detected_and_allowed() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(new PromptRepetitionPolicy(), true);
        InputGuardrailRequest request = request(UserMessage.from("How old is he?"), true);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.text()).isEqualTo("How old is he?\nHow old is he?");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.successfulText()).isEqualTo("How old is he?\nHow old is he?");
    }

    private static InputGuardrailRequest request(UserMessage userMessage, boolean ragDetected) {
        GuardrailRequestParams.Builder commonParamsBuilder =
                GuardrailRequestParams.builder().userMessageTemplate("{{it}}").variables(Map.of());

        if (ragDetected) {
            commonParamsBuilder.augmentationResult(AugmentationResult.builder()
                    .chatMessage(userMessage)
                    .contents(List.of())
                    .build());
        }

        return InputGuardrailRequest.builder()
                .userMessage(userMessage)
                .commonParams(commonParamsBuilder.build())
                .build();
    }
}
