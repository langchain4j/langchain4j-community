package dev.langchain4j.community.model.xinference;

abstract class AbstractModelInfrastructure {
    protected final String XINFERENCE_BASE_URL = System.getenv("XINFERENCE_BASE_URL");
    protected final String CHAT_MODEL_NAME = "qwen2.5-instruct";
    protected final String LANGUAGE_MODEL_NAME = "qwen2.5";
    protected final String EMBEDDING_MODEL_NAME = "bge-m3";
    protected final String IMAGE_MODEL_NAME = "sd3-medium";
    protected final String RERANK_MODEL_NAME = "bge-reranker-v2-m3";
    protected final String VL_MODEL_NAME = "qwen2-vl-instruct";
}
