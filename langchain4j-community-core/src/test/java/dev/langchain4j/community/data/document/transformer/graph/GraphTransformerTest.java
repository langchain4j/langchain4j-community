package dev.langchain4j.community.data.document.transformer.graph;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GraphTransformerTest {

    /**
     * A mock {@link GraphTransformer} which extract nodes between {@code <node></node>} and extract relationships between {@code <edge></edge>}
     */
    class MockGraphTransformer implements GraphTransformer {

        @Override
        public GraphDocument transform(Document document) {
            // TODO
            return null;
        }
    }

    @Test
    void test_transform_all() {
        
    }
}
