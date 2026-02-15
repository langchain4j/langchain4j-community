package dev.langchain4j.community.rag.query.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.prompt.repetition.PromptRepetitionMode;
import dev.langchain4j.community.prompt.repetition.PromptRepetitionPolicy;
import dev.langchain4j.community.prompt.repetition.PromptRepetitionReason;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepeatingQueryTransformerTest {

    @Test
    void should_repeat_query_and_preserve_metadata() {

        // given
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        Metadata metadata = Metadata.from(UserMessage.from("query"), "memory-id", List.of());
        Query query = Query.from("query", metadata);

        // when
        Collection<Query> transformedQueries = transformer.transform(query);

        // then
        assertThat(transformedQueries).containsExactly(Query.from("query\nquery", metadata));
    }

    @Test
    void should_skip_when_mode_is_never() {

        // given
        PromptRepetitionPolicy policy = new PromptRepetitionPolicy(PromptRepetitionMode.NEVER);
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Query query = Query.from("query");

        // when
        Collection<Query> transformedQueries = transformer.transform(query);

        // then
        assertThat(transformedQueries).containsExactly(query);
        assertThat(transformer.decide(query).reason()).isEqualTo(PromptRepetitionReason.SKIPPED_MODE_NEVER);
    }

    @Test
    void should_be_idempotent_on_already_repeated_query() {

        // given
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer();
        Query query = Query.from("query\nquery");

        // when
        Collection<Query> transformedQueries = transformer.transform(query);

        // then
        assertThat(transformedQueries).containsExactly(query);
        assertThat(transformer.decide(query).reason()).isEqualTo(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED);
    }

    @Test
    void should_skip_when_query_is_too_long_in_auto_mode() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(4)
                .reasoningKeywords(java.util.Set.of())
                .build();
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Metadata metadata = Metadata.from(UserMessage.from("query"), "memory-id", List.of());
        Query query = Query.from("query-too-long", metadata);

        // when
        Collection<Query> transformedQueries = transformer.transform(query);

        // then
        assertThat(transformedQueries).containsExactly(Query.from("query-too-long", metadata));
        assertThat(transformer.decide(query).reason()).isEqualTo(PromptRepetitionReason.SKIPPED_TOO_LONG);
    }

    @Test
    void should_skip_when_reasoning_intent_is_detected_in_auto_mode() {

        // given
        PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
                .mode(PromptRepetitionMode.AUTO)
                .maxChars(1000)
                .reasoningKeywords(java.util.Set.of("step by step"))
                .build();
        RepeatingQueryTransformer transformer = new RepeatingQueryTransformer(policy);
        Query query = Query.from("Please answer step by step");

        // when
        Collection<Query> transformedQueries = transformer.transform(query);

        // then
        assertThat(transformedQueries).containsExactly(query);
        assertThat(transformer.decide(query).reason()).isEqualTo(PromptRepetitionReason.SKIPPED_REASONING_INTENT);
    }
}
