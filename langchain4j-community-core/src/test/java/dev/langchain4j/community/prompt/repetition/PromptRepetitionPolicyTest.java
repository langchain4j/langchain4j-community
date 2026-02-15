package dev.langchain4j.community.prompt.repetition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptRepetitionPolicyTest {

    @Test
    void should_apply_repetition_when_mode_is_always() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");
        String query = "What is LangChain4j?";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.originalText()).isEqualTo(query);
        assertThat(decision.text()).isEqualTo(query + "\n" + query);
        assertThat(decision.originalChars()).isEqualTo(query.length());
        assertThat(decision.resultChars()).isEqualTo((query + "\n" + query).length());
    }

    @Test
    void should_skip_repetition_when_mode_is_never() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER);
        String query = "What is LangChain4j?";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
        assertThat(decision.text()).isEqualTo(query);
    }

    @Test
    void should_be_idempotent_when_input_is_already_repeated() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n");
        String query = "How to configure retrievers?";

        // when
        PromptRepetitionDecision first = policy.decide(query);
        PromptRepetitionDecision second = policy.decide(first.text());

        // then
        assertThat(first.applied()).isTrue();
        assertThat(second.applied()).isFalse();
        assertThat(second.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(second.text()).isEqualTo(first.text());
    }

    @Test
    void should_apply_repetition_when_mode_is_auto_and_prompt_is_short() {

        // given
        PromptRepetitionPolicy policy =
                new PromptRepetitionPolicy(PromptRepetitionMode.AUTO, "\n", 20, java.util.Set.of());
        String query = "compact prompt";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.text()).isEqualTo("compact prompt\ncompact prompt");
    }

    @Test
    void should_skip_repetition_when_mode_is_auto_and_prompt_is_too_long() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(
                PromptRepetitionMode.AUTO, "\n", 10, PromptRepetitionPolicy.DEFAULT_REASONING_KEYWORDS);
        String query = "this prompt is longer than ten chars";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_TOO_LONG);
        assertThat(decision.text()).isEqualTo(query);
    }

    @Test
    void should_skip_repetition_when_mode_is_auto_and_reasoning_intent_is_detected() {

        // given
        PromptRepetitionPolicy policy =
                new PromptRepetitionPolicy(PromptRepetitionMode.AUTO, "\n", 1000, java.util.Set.of("step by step"));
        String query = "Please solve it step by step";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_REASONING_INTENT);
        assertThat(decision.text()).isEqualTo(query);
    }

    @Test
    void should_apply_repetition_when_reasoning_keywords_are_empty() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(1000)
                .reasoningKeywords(java.util.Set.of())
                .build();
        String query = "Please solve it step by step";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
        assertThat(decision.text()).isEqualTo(query + "\n" + query);
    }

    @Test
    void should_apply_repetition_when_mode_is_always_even_if_auto_gates_would_skip() {

        // given
        PromptRepetitionPolicy policy =
                new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "\n", 3, java.util.Set.of("step by step"));
        String query = "Please solve it step by step";

        // when
        PromptRepetitionDecision decision = policy.decide(query);

        // then
        assertThat(decision.applied()).isTrue();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.APPLIED);
    }
}
