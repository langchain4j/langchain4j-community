package dev.langchain4j.community.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MiniMaxStreamingChatModelTest {

    @Test
    void should_create_streaming_model_with_builder() {
        // given/when
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName("MiniMax-M2.5")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(1024)
                .build();

        // then
        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.5");
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.7);
    }

    @Test
    void should_create_streaming_model_with_enum_model_name() {
        // given/when
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5_HIGHSPEED)
                .build();

        // then
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.5-highspeed");
    }

    @Test
    void should_fail_without_model_name() {
        // given/when/then
        assertThatThrownBy(() -> MiniMaxStreamingChatModel.builder()
                .apiKey("test-key")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_use_default_base_url() {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_return_empty_listeners_by_default() {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .build();

        assertThat(model.listeners()).isEmpty();
    }
}
