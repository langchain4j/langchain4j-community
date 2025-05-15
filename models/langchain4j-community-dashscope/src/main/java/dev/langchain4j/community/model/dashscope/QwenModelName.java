package dev.langchain4j.community.model.dashscope;

/**
 * The LLMs provided by Alibaba Cloud, performs better than most LLMs in Asia languages.
 */
public class QwenModelName {
    // Use with QwenChatModel and QwenLanguageModel
    public static final String QWEN_TURBO = "qwen-turbo"; // Qwen base model, stable version
    public static final String QWEN_TURBO_LATEST = "qwen-turbo-latest"; // Qwen base model, latest version
    public static final String QWEN_PLUS = "qwen-plus"; // Qwen plus model, stable version.
    public static final String QWEN_PLUS_LATEST = "qwen-plus-latest"; // Qwen plus model, latest version
    public static final String QWEN_MAX = "qwen-max"; // Qwen max model, stable version
    public static final String QWEN_MAX_LATEST = "qwen-max-latest"; // Qwen max model, latest version
    public static final String QWEN_LONG = "qwen-long"; // Qwen long model, 10m context
    public static final String QWEN_7B_CHAT = "qwen-7b-chat"; // Qwen open sourced 7-billion-parameters model
    public static final String QWEN_14B_CHAT = "qwen-14b-chat"; // Qwen open sourced 14-billion-parameters model
    public static final String QWEN_72B_CHAT = "qwen-72b-chat"; // Qwen open sourced 72-billion-parameters model
    public static final String QWEN1_5_7B_CHAT =
            "qwen1.5-7b-chat"; // Qwen open sourced 7-billion-parameters model (v1.5)
    public static final String QWEN1_5_14B_CHAT =
            "qwen1.5-14b-chat"; // Qwen open sourced 14-billion-parameters model (v1.5)
    public static final String QWEN1_5_32B_CHAT =
            "qwen1.5-32b-chat"; // Qwen open sourced 32-billion-parameters model (v1.5)
    public static final String QWEN1_5_72B_CHAT =
            "qwen1.5-72b-chat"; // Qwen open sourced 72-billion-parameters model (v1.5)
    public static final String QWEN2_0_5B_INSTRUCT =
            "qwen2-0.5b-instruct"; // Qwen open sourced 0.5-billion-parameters model (v2)
    public static final String QWEN2_1_5B_INSTRUCT =
            "qwen2-1.5b-instruct"; // Qwen open sourced 1.5-billion-parameters model (v2)
    public static final String QWEN2_7B_INSTRUCT =
            "qwen2-7b-instruct"; // Qwen open sourced 7-billion-parameters model (v2)
    public static final String QWEN2_72B_INSTRUCT =
            "qwen2-72b-instruct"; // Qwen open sourced 72-billion-parameters model (v2)
    public static final String QWEN2_57B_A14B_INSTRUCT =
            "qwen2-57b-a14b-instruct"; // Qwen open sourced 57-billion-parameters and 14-billion-activation-parameters
    public static final String QWEN2_5_0_5B_INSTRUCT =
            "qwen2.5-0.5b-instruct"; // Qwen open sourced 0.5-billion-parameters model (v2.5)
    public static final String QWEN2_5_1_5B_INSTRUCT =
            "qwen2.5-1.5b-instruct"; // Qwen open sourced 1.5-billion-parameters model (v2.5)
    public static final String QWEN2_5_3B_INSTRUCT =
            "qwen2.5-3b-instruct"; // Qwen open sourced 3-billion-parameters model (v2.5)
    public static final String QWEN2_5_7B_INSTRUCT =
            "qwen2.5-7b-instruct"; // Qwen open sourced 7-billion-parameters model (v2.5)
    public static final String QWEN2_5_14B_INSTRUCT =
            "qwen2.5-14b-instruct"; // Qwen open sourced 14-billion-parameters model (v2.5)
    public static final String QWEN2_5_32B_INSTRUCT =
            "qwen2.5-32b-instruct"; // Qwen open sourced 32-billion-parameters model (v2.5)
    public static final String QWEN2_5_72B_INSTRUCT =
            "qwen2.5-72b-instruct"; // Qwen open sourced 72-billion-parameters model (v2.5)
    public static final String QWEN3_0_6B = "qwen3-0.6b"; // Qwen open sourced 0.6-billion-parameters model (v3)
    public static final String QWEN3_1_7B = "qwen3-1.7b"; // Qwen open sourced 1.7-billion-parameters model (v3)
    public static final String QWEN3_4B = "qwen3-4b"; // Qwen open sourced 4-billion-parameters model (v3)
    public static final String QWEN3_8B = "qwen3-8b"; // Qwen open sourced 8-billion-parameters model (v3)
    public static final String QWEN3_14B = "qwen3-14b"; // Qwen open sourced 14-billion-parameters model (v3)
    public static final String QWEN3_32B = "qwen3-32b"; // Qwen open sourced 32-billion-parameters model (v3)
    public static final String QWEN3_30B_A3B =
            "qwen3-30b-a3b"; // Qwen open sourced 30-billion-parameters and 3-billion-activation-parameters (v3)
    public static final String QWEN3_235B_A22B =
            "qwen3-235b-a22b"; // Qwen open sourced 235-billion-parameters and 22-billion-activation-parameters (v3)
    public static final String QWEN_VL_PLUS =
            "qwen-vl-plus"; // Qwen multi-modal model, supports image and text information, stable version
    public static final String QWEN_VL_PLUS_LATEST =
            "qwen-vl-plus-latest"; // Qwen multi-modal model, supports image and text information, latest version
    public static final String QWEN_VL_MAX =
            "qwen-vl-max"; // Qwen multi-modal model, offers optimal performance, stable version
    public static final String QWEN_VL_MAX_LATEST =
            "qwen-vl-max-latest"; // Qwen multi-modal model, offers optimal performance, stable version
    public static final String QWEN_AUDIO_TURBO = "qwen-audio-turbo"; // Qwen audio understanding model, stable version
    public static final String QWEN_AUDIO_TURBO_LATEST =
            "qwen-audio-turbo-latest"; // Qwen audio understanding model, latest version
    public static final String QWEN_MT_TURBO = "qwen-mt-turbo"; // Qwen turbo model for translation
    public static final String QWEN_MT_PLUS = "qwen-mt-plus"; // Qwen plus model for translation
    public static final String QWQ_PLUS = "qwq-plus"; // Qwen reasoning model, stable version
    public static final String QWQ_PLUS_LATEST = "qwq-plus-latest"; // Qwen reasoning model, latest version

    // Use with QwenEmbeddingModel
    @Deprecated
    public static final String TEXT_EMBEDDING_V1 = "text-embedding-v1"; // Support: en, zh, es, fr, pt, id

    @Deprecated
    public static final String TEXT_EMBEDDING_V2 =
            "text-embedding-v2"; // Support: en, zh, es, fr, pt, id, ja, ko, de, ru

    public static final String TEXT_EMBEDDING_V3 = "text-embedding-v3"; // Support 50+ languages
}
