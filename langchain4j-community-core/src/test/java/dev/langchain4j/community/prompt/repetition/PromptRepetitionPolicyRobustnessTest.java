package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PromptRepetitionPolicyRobustnessTest {

    @Test
    void should_use_expected_defaults() {

        // when
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy();

        // then
        assertThat(policy.mode()).isEqualTo(PromptRepetitionPolicy.DEFAULT_MODE);
        assertThat(policy.mode()).isEqualTo(PromptRepetitionMode.ALWAYS);
        assertThat(policy.separator()).isEqualTo(PromptRepetitionPolicy.DEFAULT_SEPARATOR);
        assertThat(policy.separator()).isEqualTo("\n");
    }

    @Test
    void should_use_mode_only_constructor() {

        // when
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER);

        // then
        assertThat(policy.mode()).isEqualTo(PromptRepetitionMode.NEVER);
        assertThat(policy.separator()).isEqualTo("\n");
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
