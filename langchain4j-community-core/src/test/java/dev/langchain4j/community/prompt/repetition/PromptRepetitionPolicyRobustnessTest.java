package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PromptRepetitionPolicyRobustnessTest {

    @Test
    void should_use_expected_defaults() {

        // when
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy();

        // then
        assertThat(policy.mode()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MODE);
        assertThat(policy.mode()).isEqualTo(PromptRepetitionMode.AUTO);
        assertThat(policy.separator()).isEqualTo(PromptRepetitionPolicy.DEFAULT_SEPARATOR);
        assertThat(policy.maxChars()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MAX_CHARS);
        assertThat(policy.reasoningKeywords()).containsExactlyInAnyOrderElementsOf(
                PromptRepetitionPolicy.DEFAULT_REASONING_KEYWORDS);
    }

    @Test
    void should_use_mode_only_constructor() {

        // when
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER);

        // then
        assertThat(policy.mode()).isEqualTo(PromptRepetitionMode.NEVER);
        assertThat(policy.separator()).isEqualTo(PromptRepetitionPolicy.DEFAULT_SEPARATOR);
        assertThat(policy.maxChars()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MAX_CHARS);
        assertThat(policy.reasoningKeywords()).containsExactlyInAnyOrderElementsOf(
                PromptRepetitionPolicy.DEFAULT_REASONING_KEYWORDS);
    }

    @Test
    void should_use_mode_and_separator_constructor() {

        // when
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");

        // then
        assertThat(policy.mode()).isEqualTo(PromptRepetitionMode.ALWAYS);
        assertThat(policy.separator()).isEqualTo("::");
        assertThat(policy.maxChars()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MAX_CHARS);
    }

    @Test
    void should_use_builder_defaults() {

        // when
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder().build();

        // then
        assertThat(policy.mode()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MODE);
        assertThat(policy.separator()).isEqualTo(PromptRepetitionPolicy.DEFAULT_SEPARATOR);
        assertThat(policy.maxChars()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MAX_CHARS);
        assertThat(policy.reasoningKeywords()).containsExactlyInAnyOrderElementsOf(
                PromptRepetitionPolicy.DEFAULT_REASONING_KEYWORDS);
    }

    @Test
    void should_normalize_reasoning_keywords_and_make_them_unmodifiable() {

        // when
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .reasoningKeywords(List.of("  Step By Step ", "step by step", " ", "SHOW YOUR REASONING"))
                .build();

        // then
        assertThat(policy.reasoningKeywords()).containsExactly("step by step", "show your reasoning");
        assertThatThrownBy(() -> policy.reasoningKeywords().add("another"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_throw_when_mode_is_null() {
        assertThatThrownBy(() -> new PromptRepetitionPolicy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mode cannot be null");
    }

    @Test
    void should_throw_when_separator_is_null() {
        assertThatThrownBy(() -> new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("separator cannot be null");
    }

    @Test
    void should_throw_when_separator_is_empty() {
        assertThatThrownBy(() -> new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("separator cannot be empty");
    }

    @Test
    void should_throw_when_max_chars_is_not_positive() {
        assertThatThrownBy(() ->
                        new PromptRepetitionPolicy(PromptRepetitionMode.AUTO, "\n", 0, List.of("step by step")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxChars must be greater than zero, but is: 0");

        assertThatThrownBy(() ->
                        new PromptRepetitionPolicy(PromptRepetitionMode.AUTO, "\n", -1, List.of("step by step")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxChars must be greater than zero, but is: -1");
    }

    @Test
    void should_throw_when_reasoning_keywords_collection_is_null() {
        assertThatThrownBy(() -> new PromptRepetitionPolicy(PromptRepetitionMode.AUTO, "\n", 100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reasoningKeywords cannot be null");
    }

    @Test
    void should_throw_when_text_is_null() {
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS);
        assertThatThrownBy(() -> policy.decide(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text cannot be null");
    }

    @Test
    void should_repeat_with_custom_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");

        // when
        PromptRepetitionDecision decision = policy.decide("alpha");

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.text()).isEqualTo("alpha::alpha");
    }

    @Test
    void should_detect_already_repeated_when_original_starts_with_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        String original = "::leading";

        // when
        PromptRepetitionDecision first = policy.decide(original);
        PromptRepetitionDecision second = policy.decide(first.text());

        // then
        assertThat(first.applied()).isTrue();
        assertThat(second.applied()).isFalse();
        assertThat(second.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(second.text()).isEqualTo(first.text());
    }

    @Test
    void should_detect_already_repeated_when_original_ends_with_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        String original = "trailing::";

        // when
        PromptRepetitionDecision first = policy.decide(original);
        PromptRepetitionDecision second = policy.decide(first.text());

        // then
        assertThat(first.applied()).isTrue();
        assertThat(second.applied()).isFalse();
        assertThat(second.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(second.text()).isEqualTo(first.text());
    }

    @Test
    void should_not_treat_blank_left_half_as_repeated() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");

        // when
        PromptRepetitionDecision decision = policy.decide("\nhello");

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
    }

    @Test
    void should_prioritize_never_mode_before_idempotence_check() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER, "\n");

        // when
        PromptRepetitionDecision decision = policy.decide("q\nq");

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
    }

    @Test
    void should_prioritize_already_repeated_check_before_auto_gates() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .separator("::")
                .maxChars(4)
                .reasoningKeywords(List.of("step by step"))
                .build();
        String repeated = "hello::hello";

        // when
        PromptRepetitionDecision decision = policy.decide(repeated);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
    }

    @Test
    void should_prioritize_too_long_before_reasoning_in_auto_mode() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(8)
                .reasoningKeywords(List.of("step by step"))
                .build();

        // when
        PromptRepetitionDecision decision = policy.decide("please solve this step by step");

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_TOO_LONG);
    }

    @Test
    void should_apply_when_text_length_equals_max_chars_in_auto_mode() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(5)
                .reasoningKeywords(List.of("step by step"))
                .build();

        // when
        PromptRepetitionDecision decision = policy.decide("abcde");

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
    }

    @Test
    void should_skip_when_text_length_exceeds_max_chars_in_auto_mode() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(5)
                .reasoningKeywords(List.of("step by step"))
                .build();

        // when
        PromptRepetitionDecision decision = policy.decide("abcdef");

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_TOO_LONG);
    }

    @Test
    void should_detect_reasoning_intent_case_insensitively() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(1000)
                .reasoningKeywords(List.of("step by step"))
                .build();

        // when
        PromptRepetitionDecision decision = policy.decide("Please solve this STEP BY STEP");

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_REASONING_INTENT);
    }

    @Test
    void should_apply_in_auto_mode_when_reasoning_keywords_are_empty() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(1000)
                .reasoningKeywords(List.of())
                .build();

        // when
        PromptRepetitionDecision decision = policy.decide("Please solve this step by step");

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
    }

    @Test
    void should_apply_in_always_mode_even_if_auto_gates_would_skip() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.ALWAYS)
                .maxChars(4)
                .reasoningKeywords(List.of("step by step"))
                .build();

        // when
        PromptRepetitionDecision decision = policy.decide("please solve this step by step");

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
    }

    @Test
    void should_be_idempotent_for_many_random_inputs_with_single_char_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");
        Random random = new Random(17L);

        // then
        for (int i = 0; i < 300; i++) {
            String input = randomNonBlankText(random);
            PromptRepetitionDecision first = policy.decide(input);
            PromptRepetitionDecision second = policy.decide(first.text());

            assertThat(second.applied()).isFalse();
            assertThat(second.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
            assertThat(second.text()).isEqualTo(first.text());
        }
    }

    @Test
    void should_be_idempotent_for_many_random_inputs_with_multi_char_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        Random random = new Random(33L);

        // then
        for (int i = 0; i < 300; i++) {
            String input = randomNonBlankText(random);
            PromptRepetitionDecision first = policy.decide(input);
            PromptRepetitionDecision second = policy.decide(first.text());

            assertThat(second.applied()).isFalse();
            assertThat(second.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
            assertThat(second.text()).isEqualTo(first.text());
        }
    }

    @Test
    void should_be_idempotent_for_many_random_inputs_in_auto_mode_with_open_gates() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .separator("::")
                .maxChars(10_000)
                .reasoningKeywords(List.of())
                .build();
        Random random = new Random(87L);

        // then
        for (int i = 0; i < 300; i++) {
            String input = randomNonBlankText(random);
            PromptRepetitionDecision first = policy.decide(input);
            PromptRepetitionDecision second = policy.decide(first.text());

            assertThat(second.applied()).isFalse();
            assertThat(second.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
            assertThat(second.text()).isEqualTo(first.text());
        }
    }

    private static String randomNonBlankText(Random random) {
        String alphabet = "abcXYZ0123 :-_/\n";
        int length = 1 + random.nextInt(60);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        String value = sb.toString();
        return value.isBlank() ? "fallback-text" : value;
    }
}
