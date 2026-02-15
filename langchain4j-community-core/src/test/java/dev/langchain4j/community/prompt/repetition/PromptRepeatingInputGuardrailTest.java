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

    @Test
    void should_skip_when_reasoning_intent_is_detected_in_auto_mode() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        InputGuardrailRequest request = request(UserMessage.from("Please solve this step by step"), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_REASONING_INTENT);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_skip_when_text_is_too_long_in_auto_mode() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(10)
                .reasoningKeywords(java.util.Set.of())
                .build();
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy);
        InputGuardrailRequest request = request(UserMessage.from("This text is definitely too long"), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_TOO_LONG);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_prioritize_rag_skip_before_auto_mode_gates() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        InputGuardrailRequest request = request(UserMessage.from("Please solve this step by step"), true);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_RAG_DETECTED);
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
