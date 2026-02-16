package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.rag.AugmentationResult;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PromptRepeatingInputGuardrailRobustnessTest {

    @Test
    void should_use_default_constructor_settings() {

        // when
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();

        // then
        assertThat(guardrail.policy().mode()).isEqualTo(PromptRepetitionMode.ALWAYS);
        assertThat(guardrail.allowRagInput()).isFalse();
        assertThat(PromptRepeatingInputGuardrail.DEFAULT_ALLOW_RAG_INPUT).isFalse();
    }

    @Test
    void should_use_custom_policy_and_allow_rag_flag() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER, "::");

        // when
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy, true);

        // then
        assertThat(guardrail.policy()).isSameAs(policy);
        assertThat(guardrail.allowRagInput()).isTrue();
    }

    @Test
    void should_throw_when_policy_is_null() {
        assertThatThrownBy(() -> new PromptRepeatingInputGuardrail(null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("policy cannot be null");
    }

    @Test
    void should_throw_when_request_is_null() {
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        assertThatThrownBy(() -> guardrail.decide(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("request cannot be null");
    }

    @Test
    void should_skip_when_policy_mode_is_never() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER, "::");
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy, true);
        InputGuardrailRequest request = request(UserMessage.from("hello"), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_skip_when_input_is_already_repeated() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        InputGuardrailRequest request = request(UserMessage.from("hello\nhello"), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(decision.text()).isEqualTo("hello\nhello");
    }

    @Test
    void should_skip_when_repeated_input_starts_with_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy);
        String repeated = "::leading::::leading";
        InputGuardrailRequest request = request(UserMessage.from(repeated), false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);
        InputGuardrailResult result = guardrail.validate(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_skip_rag_before_policy_when_not_allowed() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy, false);
        InputGuardrailRequest request = request(UserMessage.from("hello"), true);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_RAG_DETECTED);
    }

    @Test
    void should_obey_policy_when_rag_is_allowed() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER, "::");
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy, true);
        InputGuardrailRequest request = request(UserMessage.from("hello"), true);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
    }

    @Test
    void should_skip_mixed_content_and_expose_first_text_for_diagnostics() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        UserMessage mixed = UserMessage.from(List.of(
                TextContent.from("first text"),
                ImageContent.from("https://example.com/image.png")));
        InputGuardrailRequest request = request(mixed, false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_NON_TEXT);
        assertThat(decision.originalText()).isEqualTo("first text");
        assertThat(decision.text()).isEqualTo("first text");
    }

    @Test
    void should_skip_image_only_content_and_expose_empty_text_for_diagnostics() {

        // given
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail();
        UserMessage imageOnly = UserMessage.from(ImageContent.from("https://example.com/image.png"));
        InputGuardrailRequest request = request(imageOnly, false);

        // when
        PromptRepetitionDecision decision = guardrail.decide(request);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_NON_TEXT);
        assertThat(decision.originalText()).isEmpty();
        assertThat(decision.text()).isEmpty();
    }

    @Test
    void should_match_underlying_policy_for_many_random_single_text_inputs() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        PromptRepeatingInputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy);
        Random random = new Random(123L);

        // then
        for (int i = 0; i < 300; i++) {
            String input = randomNonBlankText(random);
            InputGuardrailRequest request = request(UserMessage.from(input), false);

            PromptRepetitionDecision expected = policy.decide(input);
            PromptRepetitionDecision actual = guardrail.decide(request);
            InputGuardrailResult result = guardrail.validate(request);

            assertThat(actual.applied()).isEqualTo(expected.applied());
            assertThat(actual.reason()).isEqualTo(expected.reason());
            assertThat(actual.text()).isEqualTo(expected.text());
            assertThat(result.hasRewrittenResult()).isEqualTo(expected.applied());
            if (expected.applied()) {
                assertThat(result.successfulText()).isEqualTo(expected.text());
            }
        }
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

    private static String randomNonBlankText(Random random) {
        String alphabet = "abcXYZ0123 :-_/\n";
        int length = 1 + random.nextInt(50);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        String value = sb.toString();
        return value.isBlank() ? "fallback-text" : value;
    }
}
