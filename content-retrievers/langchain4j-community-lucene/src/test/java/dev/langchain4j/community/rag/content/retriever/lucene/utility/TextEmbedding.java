package dev.langchain4j.community.rag.content.retriever.lucene.utility;

import static java.util.Objects.requireNonNull;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public record TextEmbedding(String id, TextSegment text, Embedding embedding) {

    public TextEmbedding {
        requireNonNull(text, "No text provided");
        requireNonNull(embedding, "No embedding provided");
    }

    public static TextEmbedding fromResource(final String resourceName) {
        try {
            final URL resource = TextEmbedding.class.getResource("/" + resourceName);
            final Path resourcePath = Paths.get(resource.toURI());
            final List<String> lines = Files.readAllLines(resourcePath);
            if (lines.size() != 2) {
                throw new IOException("Expected 2 lines");
            }
            final var text = lines.get(0);
            final float[] array;
            final var line = lines.get(1);
            if (line != null) {
                final String[] tokens = line.split(",");
                array = new float[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    array[i] = Float.parseFloat(tokens[i]);
                }
            } else {
                array = new float[0];
            }
            return new TextEmbedding(resourceName, TextSegment.from(text), Embedding.from(array));
        } catch (final Exception e) {
            return new TextEmbedding(resourceName, TextSegment.from(e.getMessage()), Embedding.from(new float[0]));
        }
    }

    public void toFile() throws IOException {
        try (final PrintWriter writer = new PrintWriter(id())) {
            writer.println(text().text());
            final var vector = embedding.vector();
            for (int i = 0; i < vector.length; i++) {
                writer.write(Float.toString(vector[i]));
                if (i < vector.length - 1) {
                    writer.write(",");
                }
            }
            writer.println();
        }
    }
}
