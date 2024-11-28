package dev.langchain4j.community.model.xinference;

abstract class AbstractModelInfrastructure {
    public static final String XINFERENCE_BASE_URL = System.getenv("XINFERENCE_BASE_URL");
    public static final String CHAT_MODEL_NAME = "qwen2.5-instruct";
    public static final String LANGUAGE_MODEL_NAME = "qwen2.5";
    public static final String EMBEDDING_MODEL_NAME = "bge-m3";
    public static final String IMAGE_MODEL_NAME = "sd3-medium";
    public static final String RERANK_MODEL_NAME = "bge-reranker-v2-m3";
    public static final String VL_MODEL_NAME = "qwen2-vl-instruct";
}
