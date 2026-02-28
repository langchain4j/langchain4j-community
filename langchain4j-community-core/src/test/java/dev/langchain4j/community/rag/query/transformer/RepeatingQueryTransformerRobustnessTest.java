package dev.langchain4j.community.rag.query.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.prompt.repetition.PromptRepetitionDecision;
import dev.langchain4j.community.prompt.repetition.PromptRepetitionMode;
import dev.langchain4j.community.prompt.repetition.PromptRepetitionPolicy;
import dev.langchain4j.community.prompt.repetition.PromptRepetitionReason;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RepeatingQueryTransformerRobustnessTest {

    @Test
    void should_use_default_policy_from_default_constructor() {

        // when
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();

        // then
        assertThat(transformer.policy().mode()).isEqualTo(PromptRepetitionMode.ALWAYS);
        assertThat(transformer.policy().separator()).isEqualTo("\n");
    }

    @Test
    void should_throw_when_policy_is_null() {
        assertThatThrownBy(() -> new RepeatingQueryTransformer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("policy cannot be null");
    }

    @Test
    void should_throw_when_decide_query_is_null() {
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        assertThatThrownBy(() -> transformer.decide(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query cannot be null");
    }

    @Test
    void should_throw_when_transform_query_is_null() {
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        assertThatThrownBy(() -> transformer.transform(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query cannot be null");
    }

    @Test
    void should_return_single_unmodifiable_collection() {

        // given
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        Query query = Query.from("hello");

        // when
        Collection<Query> transformedQueries = transformer.transform(query);

        // then
        assertThat(transformedQueries).hasSize(1);
        assertThat(transformedQueries.iterator().next().text()).isEqualTo("hello\nhello");
        assertThatThrownBy(() -> transformedQueries.add(query)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_preserve_metadata_instance_when_transforming() {

        // given
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        Metadata metadata = Metadata.from(UserMessage.from("u"), "memory", List.of());
        Query query = Query.from("hello", metadata);

        // when
        Query transformed = transformer.transform(query).iterator().next();

        // then
        assertThat(transformed.text()).isEqualTo("hello\nhello");
        assertThat(transformed.metadata()).isSameAs(metadata);
    }

    @Test
    void should_repeat_with_custom_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Query query = Query.from("hello");

        // when
        Query transformed = transformer.transform(query).iterator().next();

        // then
        assertThat(transformed.text()).isEqualTo("hello::hello");
    }

    @Test
    void should_skip_when_mode_is_never() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER, "::");
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Query query = Query.from("hello");

        // when
        PromptRepetitionDecision decision = transformer.decide(query);
        Query transformed = transformer.transform(query).iterator().next();

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
        assertThat(transformed).isEqualTo(query);
    }

    @Test
    void should_skip_when_query_is_already_repeated() {

        // given
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        Query query = Query.from("hello\nhello");

        // when
        PromptRepetitionDecision decision = transformer.decide(query);
        Query transformed = transformer.transform(query).iterator().next();

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(transformed).isEqualTo(query);
    }

    @Test
    void should_skip_when_repeated_query_starts_with_separator() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Query query = Query.from("::leading::::leading");

        // when
        PromptRepetitionDecision decision = transformer.decide(query);
        Query transformed = transformer.transform(query).iterator().next();

        // then
        assertThat(decision.applied()).isFalse();
        assertThat(decision.reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
        assertThat(transformed).isEqualTo(query);
    }

    @Test
    void should_keep_decide_and_transform_consistent_for_many_random_queries() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.ALWAYS, "::");
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Metadata metadata = Metadata.from(UserMessage.from("u"), "memory", List.of());
        Random random = new Random(999L);

        // then
        for (int i = 0; i < 300; i++) {
            String text = randomNonBlankQueryText(random);
            Query query = (i % 2 == 0) ? Query.from(text) : Query.from(text, metadata);

            PromptRepetitionDecision decision = transformer.decide(query);
            Query transformed = transformer.transform(query).iterator().next();

            assertThat(transformed.text()).isEqualTo(decision.text());
            assertThat(transformed.metadata()).isEqualTo(query.metadata());
        }
    }

    private static String randomNonBlankQueryText(Random random) {
        String alphabet = "abcXYZ0123 :-_/\n";
        int length = 1 + random.nextInt(40);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        String value = sb.toString();
        return value.isBlank() ? "fallback-query" : value;
    }
}
