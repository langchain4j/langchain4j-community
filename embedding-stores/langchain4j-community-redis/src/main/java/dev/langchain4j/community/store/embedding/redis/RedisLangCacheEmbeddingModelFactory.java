package dev.langchain4j.community.store.embedding.redis;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating Redis LangCache embedding models.
 * <p>
 * This factory uses Java's ServiceLoader mechanism to find a suitable EmbeddingModel implementation
 * (such as HuggingFaceEmbeddingModel) to use as the delegate for the RedisLangCacheEmbeddingModel.
 * </p>
 */
public class RedisLangCacheEmbeddingModelFactory {

    private static final Logger log = LoggerFactory.getLogger(RedisLangCacheEmbeddingModelFactory.class);
    private static final String HUGGING_FACE_MODEL_CLASS =
            "dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel";
    private static final String REDIS_MODEL_ID = "redis/langcache-embed-v1";

    /**
     * Creates a new RedisLangCacheEmbeddingModel if a suitable HuggingFace embedding model implementation is available.
     * <p>
     * This will attempt to create a HuggingFaceEmbeddingModel configured with the redis/langcache-embed-v1 model.
     * If the HuggingFaceEmbeddingModel class is not available on the classpath, or the access token is not
     * configured, this method will return null.
     * </p>
     *
     * @param accessToken Optional HuggingFace API access token (can be null for public models)
     * @return A RedisLangCacheEmbeddingModel or null if unable to create one
     */
    public static EmbeddingModel create(String accessToken) {
        // First, check if HuggingFaceEmbeddingModel is available on the classpath
        try {
            Class<?> huggingFaceModelClass = Class.forName(HUGGING_FACE_MODEL_CLASS);

            // Look for a builder method
            java.lang.reflect.Method builderMethod = huggingFaceModelClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            // Configure the builder with our model
            Class<?> builderClass = builder.getClass();

            // Set the model ID
            java.lang.reflect.Method modelIdMethod = findMethod(builderClass, "modelId");
            builder = modelIdMethod.invoke(builder, REDIS_MODEL_ID);

            // Set the access token if provided
            if (accessToken != null && !accessToken.trim().isEmpty()) {
                java.lang.reflect.Method accessTokenMethod = findMethod(builderClass, "accessToken");
                builder = accessTokenMethod.invoke(builder, accessToken);
            }

            // Build the embedding model
            java.lang.reflect.Method buildMethod = findMethod(builderClass, "build");
            EmbeddingModel huggingFaceModel = (EmbeddingModel) buildMethod.invoke(builder);

            // Wrap it in our Redis-specific model
            return new RedisLangCacheEmbeddingModel(huggingFaceModel);
        } catch (Exception e) {
            log.warn("Unable to create HuggingFaceEmbeddingModel: {}", e.getMessage());
            log.warn("Make sure langchain4j-hugging-face is on the classpath.");
            log.warn("Falling back to requiring explicit embedding model configuration.");
            return null;
        }
    }

    /**
     * Helper method to find a method by name, ignoring case and handling variations.
     */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            if (method.getName().equalsIgnoreCase(methodName)) {
                return method;
            }
        }
        throw new NoSuchMethodException("Method " + methodName + " not found in " + clazz.getName());
    }
}
