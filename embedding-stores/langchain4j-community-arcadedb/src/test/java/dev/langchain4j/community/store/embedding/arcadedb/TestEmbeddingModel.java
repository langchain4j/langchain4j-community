package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Random;

/**
 * Deterministic embedding model for testing. Generates normalized random vectors seeded by text
 * hash, avoiding heavy ONNX model dependencies in test environments.
 */
class TestEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    TestEmbeddingModel(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Response<Embedding> embed(String text) {
        Random rng = new Random(text.hashCode());
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (rng.nextFloat() - 0.5f) * 2.0f;
        }
        // Normalize
        float norm = 0.0f;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) vector[i] /= norm;
        }
        return Response.from(Embedding.from(vector));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings =
                textSegments.stream().map(s -> embed(s).content()).toList();
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
