package dev.langchain4j.community.store.embedding.memfile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.store.embedding.memfile.serialization.JsonStoreSerializationStrategy;
import dev.langchain4j.community.store.embedding.memfile.serialization.StoreSerializationStrategy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MemFileEmbeddingStoreSerializationTest {

    @TempDir
    Path tempDir;

    private MemFileEmbeddingStore<TextSegment> embeddingStore;
    private StoreSerializationStrategy<TextSegment> strategy;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore = new MemFileEmbeddingStore<>(tempDir);
        strategy = new JsonStoreSerializationStrategy<>();
    }

    @AfterEach
    void tearDown() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @Test
    void should_serialize_deserialize_json_roundtrip() throws IOException {
        // given - add some comprehensive test data
        String text1 = "Machine learning enables computers to learn patterns from data";
        String text2 = "Natural language processing helps understand human communication";
        String text3 = "Computer vision allows machines to interpret visual information";

        TextSegment segment1 = TextSegment.from(text1);
        TextSegment segment2 = TextSegment.from(text2);

        embeddingStore.add(embeddingModel.embed(text1).content(), segment1);
        embeddingStore.add(embeddingModel.embed(text2).content(), segment2);
        embeddingStore.add(embeddingModel.embed(text3).content()); // embedding only

        // when - serialize to JSON string and deserialize back
        String json = embeddingStore.serialize(strategy);
        MemFileEmbeddingStore<TextSegment> deserializedStore = embeddingStore.deserialize(strategy, json);

        // then - validate JSON structure and content preservation
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(json);

        // Validate JSON structure
        assertThat(rootNode.has("entries")).isTrue();
        assertThat(rootNode.has("chunkStorageDirectory")).isTrue();
        assertThat(rootNode.has("cacheSize")).isTrue();
        assertThat(rootNode.get("entries").isArray()).isTrue();
        assertThat(rootNode.get("entries").size()).isEqualTo(3);

        // Validate entries have proper structure
        for (JsonNode entryNode : rootNode.get("entries")) {
            assertThat(entryNode.has("id")).isTrue();
            assertThat(entryNode.has("embedding")).isTrue();
            assertThat(entryNode.get("embedding").has("vector")).isTrue();
            assertThat(entryNode.get("embedding").get("vector").isArray()).isTrue();
            assertThat(entryNode.get("embedding").get("vector").size()).isGreaterThan(0);
        }

        // Validate deserialized store functionality
        assertThat(deserializedStore).isNotNull();
        assertThat(deserializedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(text1).content())
                                .maxResults(10)
                                .build())
                        .matches())
                .hasSize(3); // Should
        // find
        // similar
        // entries

        // Validate specific searches can find the exact content
        assertThat(deserializedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(text1).content())
                                .maxResults(1)
                                .build())
                        .matches()
                        .get(0)
                        .embedded()
                        .text())
                .isEqualTo(text1);
    }

    @Test
    void should_serialize_deserialize_file_roundtrip() throws IOException {
        // given - add test data with different scenarios
        String text1 = "Artificial intelligence transforms various industries worldwide";
        String text2 = "Deep learning uses neural networks for complex pattern recognition";

        TextSegment segment1 = TextSegment.from(text1);
        TextSegment segment2 = TextSegment.from(text2);

        embeddingStore.add("custom-id-1", embeddingModel.embed(text1).content(), segment1);
        embeddingStore.add("custom-id-2", embeddingModel.embed(text2).content(), segment2);
        embeddingStore.add(embeddingModel.embed("embedding-only-text").content()); // no TextSegment

        Path file = tempDir.resolve("store_roundtrip.json");

        // when - serialize to file and deserialize back
        embeddingStore.serializeToFile(strategy, file);
        MemFileEmbeddingStore<TextSegment> deserializedStore = embeddingStore.deserializeFromFile(strategy, file);

        // then - validate file was created and content preserved
        assertThat(Files.exists(file)).isTrue();
        String fileContent = Files.readString(file);
        assertThat(fileContent).contains("custom-id-1");
        assertThat(fileContent).contains("custom-id-2");
        assertThat(fileContent).contains("entries");
        assertThat(fileContent).contains("chunkStorageDirectory");

        // Validate deserialized store has all data
        assertThat(deserializedStore).isNotNull();

        // Should find 3 entries total
        assertThat(deserializedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel
                                        .embed("intelligence artificial")
                                        .content())
                                .maxResults(10)
                                .build())
                        .matches())
                .hasSize(3);

        // Should find specific entries by their content
        assertThat(deserializedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(text1).content())
                                .maxResults(1)
                                .build())
                        .matches()
                        .get(0)
                        .embedded()
                        .text())
                .isEqualTo(text1);

        assertThat(deserializedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed(text2).content())
                                .maxResults(1)
                                .build())
                        .matches()
                        .get(0)
                        .embedded()
                        .text())
                .isEqualTo(text2);
    }

    @Test
    void should_handle_empty_store_serialization() throws IOException {
        // given - empty store

        // when - serialize and deserialize empty store
        String json = embeddingStore.serialize(strategy);
        MemFileEmbeddingStore<TextSegment> deserializedStore = embeddingStore.deserialize(strategy, json);

        // then - validate empty store structure and functionality
        assertThat(json).isNotNull().isNotEmpty();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(json);
        assertThat(rootNode.has("entries")).isTrue();
        assertThat(rootNode.has("chunkStorageDirectory")).isTrue();
        assertThat(rootNode.has("cacheSize")).isTrue();
        assertThat(rootNode.get("entries").isArray()).isTrue();
        assertThat(rootNode.get("entries").size()).isEqualTo(0);

        // Validate deserialized empty store works
        assertThat(deserializedStore).isNotNull();
        assertThat(deserializedStore
                        .search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(embeddingModel.embed("any text").content())
                                .maxResults(10)
                                .build())
                        .matches())
                .isEmpty();
    }
}
