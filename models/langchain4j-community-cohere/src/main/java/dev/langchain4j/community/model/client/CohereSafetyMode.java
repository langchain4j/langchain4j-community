package dev.langchain4j.community.model.client;

/**
 * Cohere safety instruction variant, which moderates the model's responses.
 * <br/>
 * More details are available <a href="https://docs.cohere.com/v2/docs/safety-modes">here</a>.
 */
public enum CohereSafetyMode {

    /**
     * Fewer safety constraints on output, while maintaining core protection.
     */
    CONTEXTUAL,

    /**
     * Forces the model to avoid all sensitive topics.
     */
    STRICT,

    /**
     * Disables safety constraints.
     */
    OFF
}
