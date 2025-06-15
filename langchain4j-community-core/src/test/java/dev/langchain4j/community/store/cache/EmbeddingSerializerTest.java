package dev.langchain4j.community.store.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingSerializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Embedding.class, new EmbeddingSerializer());
        objectMapper.registerModule(module);
    }

    @Test
    void should_serialize_embedding_with_values() throws JsonProcessingException {
        Embedding embedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});

        String json = objectMapper.writeValueAsString(embedding);

        assertThat(json).isEqualTo("{\"embedding\":[0.1,0.2,0.3]}");
    }

    @Test
    void should_serialize_empty_embedding() throws JsonProcessingException {
        Embedding embedding = Embedding.from(new float[]{});

        String json = objectMapper.writeValueAsString(embedding);

        assertThat(json).isEqualTo("{\"embedding\":[]}");
    }

    @Test
    void should_construct_using_no_arg_constructor() {
        EmbeddingSerializer serializer = new EmbeddingSerializer();
        assertThat(serializer).isNotNull();
    }

    @Test
    void should_construct_using_class_constructor() {
        EmbeddingSerializer serializer = new EmbeddingSerializer(Embedding.class);
        assertThat(serializer).isNotNull();
    }
}
