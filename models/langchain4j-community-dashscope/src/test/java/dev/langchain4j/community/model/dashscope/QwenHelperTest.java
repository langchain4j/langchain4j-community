package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenHelper.isMultimodalModelName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationTokensDetails;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationUsage;
import com.alibaba.dashscope.common.DashScopeResult;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class QwenHelperTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_include_thinking_budget_in_multimodal_request(boolean incrementalOutput) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Solve this problem"))
                .parameters(QwenChatRequestParameters.builder()
                        .modelName(QwenModelName.QWEN3_VL_PLUS)
                        .enableThinking(true)
                        .thinkingBudget(1024)
                        .build())
                .build();

        JsonObject parameters = QwenHelper.toMultiModalConversationParam(
                        "test-api-key", request, null, incrementalOutput)
                .getHttpBody()
                .getAsJsonObject("parameters");

        assertThat(parameters.has("thinking_budget")).isTrue();
        assertThat(parameters.get("thinking_budget").getAsInt()).isEqualTo(1024);
    }

    @Test
    void should_omit_unset_thinking_budget_from_multimodal_request() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Solve this problem"))
                .parameters(QwenChatRequestParameters.builder()
                        .modelName(QwenModelName.QWEN3_VL_PLUS)
                        .enableThinking(true)
                        .build())
                .build();

        JsonObject parameters = QwenHelper.toMultiModalConversationParam("test-api-key", request, null, false)
                .getHttpBody()
                .getAsJsonObject("parameters");

        assertThat(parameters.has("thinking_budget")).isFalse();
    }

    @Test
    void should_allow_customizer_to_override_multimodal_thinking_budget() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Solve this problem"))
                .parameters(QwenChatRequestParameters.builder()
                        .modelName(QwenModelName.QWEN3_VL_PLUS)
                        .enableThinking(true)
                        .thinkingBudget(1024)
                        .build())
                .build();

        JsonObject parameters = QwenHelper.toMultiModalConversationParam(
                        "test-api-key", request, builder -> builder.thinkingBudget(2048), false)
                .getHttpBody()
                .getAsJsonObject("parameters");

        assertThat(parameters.get("thinking_budget").getAsInt()).isEqualTo(2048);
    }

    @Test
    void should_map_cached_tokens_from_generation_result() {
        GenerationUsage usage = GenerationUsage.builder()
                .inputTokens(1000)
                .outputTokens(20)
                .totalTokens(1020)
                .promptTokensDetails(GenerationUsage.PromptTokensDetails.builder()
                        .cachedTokens(768)
                        .cacheCreationInputTokens(232)
                        .build())
                .build();
        DashScopeResult emptyResult = new DashScopeResult();
        emptyResult.setOutput(new JsonObject());
        GenerationResult result = GenerationResult.fromDashScopeResult(emptyResult);
        result.setUsage(usage);

        TokenUsage tokenUsage = QwenHelper.tokenUsageFrom(result);

        assertThat(tokenUsage).isInstanceOf(QwenTokenUsage.class);
        QwenTokenUsage qwenTokenUsage = (QwenTokenUsage) tokenUsage;
        assertThat(qwenTokenUsage.inputTokenCount()).isEqualTo(1000);
        assertThat(qwenTokenUsage.outputTokenCount()).isEqualTo(20);
        assertThat(qwenTokenUsage.totalTokenCount()).isEqualTo(1020);
        assertThat(qwenTokenUsage.cachedInputTokens()).isEqualTo(768);
        assertThat(qwenTokenUsage.cacheCreationInputTokens()).isEqualTo(232);
    }

    @Test
    void should_map_cached_tokens_from_multi_modal_conversation_result() {
        MultiModalConversationTokensDetails details = new MultiModalConversationTokensDetails();
        details.setCachedTokens(512);
        details.setCacheCreationInputTokens(488);
        MultiModalConversationUsage usage = new MultiModalConversationUsage();
        usage.setInputTokens(1000);
        usage.setOutputTokens(20);
        usage.setTotalTokens(1020);
        usage.setPromptTokensDetails(details);
        MultiModalConversationResult result = newMultiModalResult();
        result.setUsage(usage);

        TokenUsage tokenUsage = QwenHelper.tokenUsageFrom(result);

        assertThat(tokenUsage).isInstanceOf(QwenTokenUsage.class);
        QwenTokenUsage qwenTokenUsage = (QwenTokenUsage) tokenUsage;
        assertThat(qwenTokenUsage.inputTokenCount()).isEqualTo(1000);
        assertThat(qwenTokenUsage.cachedInputTokens()).isEqualTo(512);
        assertThat(qwenTokenUsage.cacheCreationInputTokens()).isEqualTo(488);
    }

    @Test
    void should_return_null_cache_fields_when_details_are_absent() {
        GenerationUsage usage = GenerationUsage.builder()
                .inputTokens(100)
                .outputTokens(20)
                .totalTokens(120)
                .build();
        GenerationResult result = newResult();
        result.setUsage(usage);

        QwenTokenUsage tokenUsage = (QwenTokenUsage) QwenHelper.tokenUsageFrom(result);

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(100);
        assertThat(tokenUsage.cachedInputTokens()).isNull();
        assertThat(tokenUsage.cacheCreationInputTokens()).isNull();
    }

    private static GenerationResult newResult() {
        DashScopeResult emptyResult = new DashScopeResult();
        emptyResult.setOutput(new JsonObject());
        return GenerationResult.fromDashScopeResult(emptyResult);
    }

    private static MultiModalConversationResult newMultiModalResult() {
        DashScopeResult emptyResult = new DashScopeResult();
        emptyResult.setOutput(new JsonObject());
        return MultiModalConversationResult.fromDashScopeResult(emptyResult);
    }

    @Test
    void should_judge_model_type_by_modelName() {
        // should be treated as multimodal models
        assertTrue(isMultimodalModelName("qwen-vl-plus"));
        assertTrue(isMultimodalModelName("qwen-audio-plus"));
        assertTrue(isMultimodalModelName("qwen3-omni-flash"));
        assertTrue(isMultimodalModelName("qwen3.5-omni-flash"));
        assertTrue(isMultimodalModelName("qwen-image-2.0"));
        assertTrue(isMultimodalModelName("qwen3-asr-flash"));
        assertTrue(isMultimodalModelName("qwen3-tts-instruct-flash"));
        assertTrue(isMultimodalModelName("qwen3.6-max-preview"));
        assertTrue(isMultimodalModelName("qwen3.5-plus"));
        assertTrue(isMultimodalModelName("qwen3.6-flash-2026-04-16"));

        // should be treated as text-only models
        assertFalse(isMultimodalModelName("qwen-max"));
        assertFalse(isMultimodalModelName("qwen3-max"));
        assertFalse(isMultimodalModelName("qwen-plus"));
    }
}
