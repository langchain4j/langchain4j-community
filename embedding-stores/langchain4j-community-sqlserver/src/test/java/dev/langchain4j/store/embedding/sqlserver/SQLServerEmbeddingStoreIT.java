package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil.DEFAULT_CONTAINER;
import static dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil.getSqlServerDataSource;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.sqlserver.util.SQLServerTestsUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SQLServerEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    static String tableName = "test_" + nextInt(1000, 2000);
    static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    static EmbeddingStore<TextSegment> embeddingStore = SQLServerEmbeddingStore.dataSourceBuilder()
            .dataSource(getSqlServerDataSource())
            .embeddingTable(EmbeddingTable.builder()
                    .name(tableName)
                    .createOption(CreateOption.CREATE_OR_REPLACE)
                    .dimension(embeddingModel.dimension())
                    .build())
            .build();

    @Test
    void test_unicode_embeddings() {

        TextSegment[] segments = SQLServerTestsUtil.japaneseSampling();
        List<Embedding> embeddings = new ArrayList<>(segments.length);
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddings.add(embedding);
        }

        List<String> ids = embeddingStore().addAll(embeddings, Arrays.asList(segments));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSameSizeAs(segments));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(0))
                .maxResults(1)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(ids.get(0));
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embeddings.get(0));
        }

        assertThat(match.embedded().text()).isEqualTo(segments[0].text());
    }

    @Test
    void test_unicode_metadata() {
        TextSegment[] segments = SQLServerTestsUtil.japaneseSampling();
        List<Embedding> embeddings = new ArrayList<>(segments.length);
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddings.add(embedding);
        }

        Embedding emb = embeddingModel().embed("Test embedding").content();

        embeddingStore().addAll(embeddings, Arrays.asList(segments));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSameSizeAs(segments));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(emb)
                .filter(MetadataFilterBuilder.metadataKey("jap_name")
                        .isEqualTo("天ぷら (てんぷら)")
                        .and(MetadataFilterBuilder.metadataKey("jap_name").isNotEqualTo("ラーメン")))
                .maxResults(2)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);

        assertThat(match.embedded().metadata().getString("name")).isEqualTo("Tempura");
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @BeforeEach
    void before() {
        clearStore();
    }

    @AfterEach
    void after() {
        clearStore();
    }

    @AfterAll
    static void afterClass() {
        DEFAULT_CONTAINER.stop();
    }
}
