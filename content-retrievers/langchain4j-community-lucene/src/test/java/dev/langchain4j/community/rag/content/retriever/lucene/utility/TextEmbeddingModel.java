package dev.langchain4j.community.rag.content.retriever.lucene.utility;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextEmbeddingModel implements EmbeddingModel {

    private final Map<String, Embedding> embeddings;

    public TextEmbeddingModel(TextEmbedding textEmbedding) {
        embeddings = new HashMap<>();
        ensureNotNull(textEmbedding, "textEmbedding");
        embeddings.put(textEmbedding.text().text(), textEmbedding.embedding());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null) {
            return null;
        }
        List<Embedding> embeddingsResponse = new ArrayList<>();
        for (TextSegment textSegment : textSegments) {
            Embedding embedding = embeddings.get(textSegment.text());
            embeddingsResponse.add(embedding);
        }

        return Response.from(embeddingsResponse);
    }
}
