package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenHelper.isMultimodalModelName;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class QwenHelperTest {

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
