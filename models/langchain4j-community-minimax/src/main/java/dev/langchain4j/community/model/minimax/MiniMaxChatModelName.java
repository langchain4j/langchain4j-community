package dev.langchain4j.community.model.minimax;

/**
 * Enum representing available MiniMax chat model names.
 * <p>
 * MiniMax provides an OpenAI-compatible API at {@code https://api.minimax.io/v1}.
 *
 * @see <a href="https://platform.minimaxi.com/document/Models">MiniMax Models Documentation</a>
 */
public enum MiniMaxChatModelName {

    /**
     * MiniMax-M2.7 — the latest and most capable model.
     */
    MINIMAX_M2_7("MiniMax-M2.7"),

    /**
     * MiniMax-M2.5 — a high-performance model with 204K context.
     */
    MINIMAX_M2_5("MiniMax-M2.5"),

    /**
     * MiniMax-M2.5-highspeed — optimized for speed with 204K context.
     */
    MINIMAX_M2_5_HIGHSPEED("MiniMax-M2.5-highspeed");

    private final String value;

    MiniMaxChatModelName(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
