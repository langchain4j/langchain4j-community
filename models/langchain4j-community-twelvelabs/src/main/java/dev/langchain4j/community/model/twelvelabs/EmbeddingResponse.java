package dev.langchain4j.community.model.twelvelabs;

import java.util.List;

/**
 * Response from the TwelveLabs
 * <a href="https://docs.twelvelabs.io/v1.3/api-reference/text-image-embeddings/create-text-embeddings">Create text embeddings</a>
 * endpoint.
 *
 * <p>The relevant payload looks like:
 * <pre>{@code
 * {
 *   "model_name": "marengo3.0",
 *   "text_embedding": {
 *     "segments": [
 *       { "float": [ ... ] }
 *     ]
 *   }
 * }
 * }</pre>
 */
class EmbeddingResponse {

    private String modelName;
    private TextEmbedding textEmbedding;

    public String getModelName() {
        return modelName;
    }

    public TextEmbedding getTextEmbedding() {
        return textEmbedding;
    }

    static class TextEmbedding {

        private List<Segment> segments;

        public List<Segment> getSegments() {
            return segments;
        }
    }

    static class Segment {

        private List<Float> floatValue;

        // The TwelveLabs API names this JSON field "float", which is a Java reserved word.
        @com.fasterxml.jackson.annotation.JsonProperty("float")
        public List<Float> getFloat() {
            return floatValue;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("float")
        public void setFloat(List<Float> floatValue) {
            this.floatValue = floatValue;
        }
    }
}
