package dev.langchain4j.community.store.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddingDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Embedding.class, new EmbeddingDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    void should_deserialize_valid_embedding() throws IOException {
        String json = "{ \"embedding\": [0.1, 0.2, 0.3] }";

        Embedding embedding = objectMapper.readValue(json, Embedding.class);

        assertThat(embedding).isNotNull();
        assertThat(embedding.vector()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void should_deserialize_empty_embedding() throws IOException {
        String json = "{ \"embedding\": [] }";

        Embedding embedding = objectMapper.readValue(json, Embedding.class);

        assertThat(embedding).isNotNull();
        assertThat(embedding.vector()).isEmpty();
    }

    @Test
    void should_throw_exception_when_embedding_is_missing() {
        String json = "{ \"not_embedding\": [1.0, 2.0] }";

        assertThatThrownBy(() -> objectMapper.readValue(json, Embedding.class))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid Embedding format");
    }

    @Test
    void should_throw_exception_when_embedding_is_not_array() {
        String json = "{ \"embedding\": 123 }";

        assertThatThrownBy(() -> objectMapper.readValue(json, Embedding.class))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid Embedding format");
    }

    @Test
    void should_construct_using_no_arg_constructor() {
        EmbeddingDeserializer deserializer = new EmbeddingDeserializer();
        assertThat(deserializer).isNotNull();
    }

    @Test
    void should_construct_using_class_constructor() {
        EmbeddingDeserializer deserializer = new EmbeddingDeserializer(Embedding.class);
        assertThat(deserializer).isNotNull();
    }
}
