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
}
