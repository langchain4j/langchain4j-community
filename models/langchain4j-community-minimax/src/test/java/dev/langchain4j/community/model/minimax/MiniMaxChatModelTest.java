package dev.langchain4j.community.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MiniMaxChatModelTest {

    @Test
    void should_create_model_with_builder() {
        // given/when
        MiniMaxChatModel model = MiniMaxChatModel.builder()
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
        assertThat(model.defaultRequestParameters().topP()).isEqualTo(0.9);
        assertThat(model.defaultRequestParameters().maxOutputTokens()).isEqualTo(1024);
    }

    @Test
    void should_create_model_with_enum_model_name() {
        // given/when
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7)
                .build();

        // then
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.7");
    }

    @Test
    void should_fail_without_model_name() {
        // given/when/then
        assertThatThrownBy(() -> MiniMaxChatModel.builder()
                .apiKey("test-key")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_use_default_base_url() {
        // should not throw - default baseUrl is applied
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_accept_custom_base_url() {
        // should not throw
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .baseUrl("https://custom-api.example.com/v1")
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_have_correct_model_name_enum_values() {
        assertThat(MiniMaxChatModelName.MINIMAX_M2_7.toString()).isEqualTo("MiniMax-M2.7");
        assertThat(MiniMaxChatModelName.MINIMAX_M2_5.toString()).isEqualTo("MiniMax-M2.5");
        assertThat(MiniMaxChatModelName.MINIMAX_M2_5_HIGHSPEED.toString()).isEqualTo("MiniMax-M2.5-highspeed");
    }

    @Test
    void should_return_empty_listeners_by_default() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
                .build();

        assertThat(model.listeners()).isEmpty();
    }
}
