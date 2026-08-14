package dev.langchain4j.community.model.twelvelabs;

import java.util.HashMap;
import java.util.Map;

/**
 * Known TwelveLabs Marengo embedding models and their output dimensions.
 *
 * @see <a href="https://docs.twelvelabs.io/v1.3/docs/concepts/models/marengo">Marengo models</a>
 */
public enum TwelveLabsEmbeddingModelName {
    MARENGO_3("marengo3.0", 512);

    private final String stringValue;
    private final Integer dimension;

    TwelveLabsEmbeddingModelName(String stringValue, Integer dimension) {
        this.stringValue = stringValue;
        this.dimension = dimension;
    }

    @Override
    public String toString() {
        return stringValue;
    }

    public Integer dimension() {
        return dimension;
    }

    private static final Map<String, Integer> KNOWN_DIMENSION =
            new HashMap<>(TwelveLabsEmbeddingModelName.values().length);

    static {
        for (TwelveLabsEmbeddingModelName embeddingModelName : TwelveLabsEmbeddingModelName.values()) {
            KNOWN_DIMENSION.put(embeddingModelName.toString(), embeddingModelName.dimension());
        }
    }

    public static Integer knownDimension(String modelName) {
        return KNOWN_DIMENSION.get(modelName);
    }
}
