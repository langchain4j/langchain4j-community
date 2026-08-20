package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel.GLM_4_FLASH;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.zhipu.chat.Thinking;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import org.junit.jupiter.api.Test;

class ZhipuAiChatRequestParametersTest {

    @Test
    void should_override_when_using_zhipu_chat_request_parameters() {
        // given
        ZhipuAiChatRequestParameters original = ZhipuAiChatRequestParameters.builder()
                .modelName(GLM_4_FLASH.toString())
                .temperature(0.7)
                .maxOutputTokens(500)
                .topP(0.9)
                .frequencyPenalty(0.1)
                .presencePenalty(0.1)
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(ResponseFormat.TEXT)
                .doSample(true)
                .thinking(
                        Thinking.builder().type("enabled").clearThinking(false).build())
                .build();

        ZhipuAiChatRequestParameters override = ZhipuAiChatRequestParameters.builder()
                .temperature(0.3)
                .maxOutputTokens(1000)
                .doSample(false)
                .thinking(
                        Thinking.builder().type("disabled").clearThinking(true).build())
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result).isInstanceOf(ZhipuAiChatRequestParameters.class);
        ZhipuAiChatRequestParameters zhipuResult = (ZhipuAiChatRequestParameters) result;

        // overridden common fields
        assertThat(zhipuResult.temperature()).isEqualTo(0.3);
        assertThat(zhipuResult.maxOutputTokens()).isEqualTo(1000);

        // preserved original fields
        assertThat(zhipuResult.modelName()).isEqualTo(GLM_4_FLASH.toString());
        assertThat(zhipuResult.topP()).isEqualTo(0.9);
        assertThat(zhipuResult.frequencyPenalty()).isEqualTo(0.1);
        assertThat(zhipuResult.presencePenalty()).isEqualTo(0.1);

        // overridden Zhipu-specific fields
        assertThat(zhipuResult.doSample()).isFalse();
        assertThat(zhipuResult.thinking()).isNotNull();
        assertThat(zhipuResult.thinking().getType()).isEqualTo("disabled");
        assertThat(zhipuResult.thinking().isClearThinking()).isTrue();
    }

    @Test
    void should_preserve_zhipu_specific_fields_when_not_overridden() {
        // given
        ZhipuAiChatRequestParameters original = ZhipuAiChatRequestParameters.builder()
                .modelName(GLM_4_FLASH.toString())
                .doSample(true)
                .thinking(
                        Thinking.builder().type("enabled").clearThinking(false).build())
                .build();

        ZhipuAiChatRequestParameters override =
                ZhipuAiChatRequestParameters.builder().temperature(0.5).build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        ZhipuAiChatRequestParameters zhipuResult = (ZhipuAiChatRequestParameters) result;
        assertThat(zhipuResult.doSample()).isTrue();
        assertThat(zhipuResult.thinking().getType()).isEqualTo("enabled");
        assertThat(zhipuResult.thinking().isClearThinking()).isFalse();
        assertThat(zhipuResult.temperature()).isEqualTo(0.5);
    }

    @Test
    void should_retain_zhipu_specific_fields_when_defaulted_by_generic_parameters() {
        // given
        ZhipuAiChatRequestParameters parameters = ZhipuAiChatRequestParameters.builder()
                .modelName("receiver-model")
                .doSample(true)
                .toolStream(true)
                .thinking(
                        Thinking.builder().type("enabled").clearThinking(false).build())
                .build();
        ChatRequestParameters defaults = DefaultChatRequestParameters.builder()
                .modelName("default-model")
                .temperature(0.4)
                .maxOutputTokens(512)
                .build();

        // when
        ChatRequestParameters result = parameters.defaultedBy(defaults);

        // then
        assertThat(result).isInstanceOf(ZhipuAiChatRequestParameters.class);
        ZhipuAiChatRequestParameters zhipuResult = (ZhipuAiChatRequestParameters) result;
        assertThat(zhipuResult.modelName()).isEqualTo("receiver-model");
        assertThat(zhipuResult.temperature()).isEqualTo(0.4);
        assertThat(zhipuResult.maxOutputTokens()).isEqualTo(512);
        assertThat(zhipuResult.doSample()).isTrue();
        assertThat(zhipuResult.toolStream()).isTrue();
        assertThat(zhipuResult.thinking()).isEqualTo(parameters.thinking());
    }

    @Test
    void should_default_missing_zhipu_fields_from_zhipu_parameters() {
        // given
        ZhipuAiChatRequestParameters parameters = ZhipuAiChatRequestParameters.builder()
                .modelName("receiver-model")
                .temperature(0.7)
                .doSample(false)
                .build();
        ZhipuAiChatRequestParameters defaults = ZhipuAiChatRequestParameters.builder()
                .modelName("default-model")
                .temperature(0.4)
                .maxOutputTokens(512)
                .doSample(true)
                .toolStream(true)
                .thinking(
                        Thinking.builder().type("enabled").clearThinking(false).build())
                .build();

        // when
        ChatRequestParameters result = parameters.defaultedBy(defaults);

        // then
        assertThat(result).isInstanceOf(ZhipuAiChatRequestParameters.class);
        ZhipuAiChatRequestParameters zhipuResult = (ZhipuAiChatRequestParameters) result;
        assertThat(zhipuResult.modelName()).isEqualTo("receiver-model");
        assertThat(zhipuResult.temperature()).isEqualTo(0.7);
        assertThat(zhipuResult.maxOutputTokens()).isEqualTo(512);
        assertThat(zhipuResult.doSample()).isFalse();
        assertThat(zhipuResult.toolStream()).isTrue();
        assertThat(zhipuResult.thinking()).isEqualTo(defaults.thinking());
    }
}
