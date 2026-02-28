package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PromptRepetitionDecisionTest {

    @Test
    void should_create_applied_decision() {

        // when
        PromptRepetitionDecision decision = PromptRepetitionDecision.applied("q", "q\nq");

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.originalText()).isEqualTo("q");
        assertThat(decision.text()).isEqualTo("q\nq");
        assertThat(decision.originalChars()).isEqualTo(1);
        assertThat(decision.resultChars()).isEqualTo(3);
    }

    @Test
    void should_create_skipped_decision() {

        // when
        PromptRepetitionDecision decision =
                PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_MODE_NEVER, "q");

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
        assertThat(decision.originalText()).isEqualTo("q");
        assertThat(decision.text()).isEqualTo("q");
        assertThat(decision.originalChars()).isEqualTo(1);
        assertThat(decision.resultChars()).isEqualTo(1);
    }

    @Test
    void should_throw_when_skipped_reason_is_applied() {
        assertThatThrownBy(() -> PromptRepetitionDecision.skipped(PromptRepetitionReason.APPLIED, "q"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use 'applied' factory method for APPLIED reason");
    }

    @Test
    void should_throw_when_reason_is_null() {
        assertThatThrownBy(() -> PromptRepetitionDecision.skipped(null, "q"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason cannot be null");
    }

    @Test
    void should_throw_when_text_is_null_for_skipped() {
        assertThatThrownBy(() -> PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_MODE_NEVER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("originalText cannot be null");
    }

    @Test
    void should_throw_when_original_text_is_null_for_applied() {
        assertThatThrownBy(() -> PromptRepetitionDecision.applied(null, "q"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("originalText cannot be null");
    }

    @Test
    void should_throw_when_result_text_is_null_for_applied() {
        assertThatThrownBy(() -> PromptRepetitionDecision.applied("q", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text cannot be null");
    }

    @Test
    void should_implement_equals_hashcode_and_to_string() {

        // given
        PromptRepetitionDecision a = PromptRepetitionDecision.applied("q", "q\nq");
        PromptRepetitionDecision b = PromptRepetitionDecision.applied("q", "q\nq");
        PromptRepetitionDecision c = PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_MODE_NEVER, "q");

        // then
        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo("not a decision");
        assertThat(a).isNotEqualTo(null);
        assertThat(a.toString()).contains("PromptRepetitionDecision").contains("applied=true");
    }
}
