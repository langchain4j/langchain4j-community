package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple deterministic embedding model for testing.
 * Generates consistent embeddings based on text hash codes.
 */
class TestEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    TestEmbeddingModel(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : textSegments) {
            embeddings.add(generateEmbedding(segment.text()));
        }
        return Response.from(embeddings);
    }

    private Embedding generateEmbedding(String text) {
        // Generate vectors with a shared base direction plus text-dependent perturbation.
        // The perturbation is large enough that different texts have meaningfully different
        // scores (not too close to 1.0), but all share a positive base so scores stay > 0.
        Random random = new Random(text.hashCode());
        float[] vector = new float[dimension];
        float norm = 0;
        for (int i = 0; i < dimension; i++) {
            float base = 1.0f;
            float perturbation = (random.nextFloat() * 2 - 1) * 0.8f;
            vector[i] = base + perturbation;
            norm += vector[i] * vector[i];
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dimension; i++) {
            vector[i] /= norm;
        }
        return Embedding.from(vector);
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
