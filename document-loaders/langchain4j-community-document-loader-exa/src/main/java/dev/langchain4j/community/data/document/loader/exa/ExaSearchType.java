package dev.langchain4j.community.data.document.loader.exa;

/**
 * Exa search types supported by the Exa Search API.
 * <p>
 * These correspond to the `type` parameter in Exa search calls:
 * <ul>
 * <li>{@code fast} – Optimized for low latency (sub‑second) factual
 * queries.</li>
 * <li>{@code auto} – Default mode providing a balance between quality and
 * speed.</li>
 * <li>{@code deep} – Comprehensive search with query expansion for
 * research/agentic use cases.</li>
 * <li>{@code neural} – Embeddings/semantic similarity based exploratory
 * search.</li>
 * </ul>
 * See the Exa docs: https://exa.ai/docs/reference/evaluating-exa-search for
 * details.:contentReference[oaicite:1]{index=1}
 */
public enum ExaSearchType {

    /**
     * Balanced search type combining quality and reasonable latency. Typically the
     * default if no type is specified.
     */
    AUTO("auto"),

    /**
     * Fast search optimized for low latency, good for straightforward, single‑step
     * factual queries.
     */
    FAST("fast"),

    /**
     * Deep search optimized for comprehensive retrieval, multi‑step and agentic
     * workflows. May involve query variations and richer context.
     */
    DEEP("deep"),

    /**
     * Neural/semantic search optimized for thematic and exploratory queries using
     * embeddings and semantic similarity techniques.
     */
    NEURAL("neural");

    private final String value;

    ExaSearchType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
