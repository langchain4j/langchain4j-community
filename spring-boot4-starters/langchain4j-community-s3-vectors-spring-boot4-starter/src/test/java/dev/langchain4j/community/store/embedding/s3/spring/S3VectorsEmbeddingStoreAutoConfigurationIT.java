package dev.langchain4j.community.store.embedding.s3.spring;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.community.store.embedding.s3.S3VectorsEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.spring.EmbeddingStoreAutoConfigurationIT;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.ApplicationContext;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorBucketRequest;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class S3VectorsEmbeddingStoreAutoConfigurationIT extends EmbeddingStoreAutoConfigurationIT {

    private static final String TEST_BUCKET_PREFIX = "langchain4j-starter-test-";
    private static final String TEST_INDEX_PREFIX = "test-index-";

    private static S3VectorsClient s3VectorsClient;
    private static String bucketName;
    private static String region;
    private static boolean bucketCreatedByTest = false;

    private String indexName;

    @BeforeAll
    static void beforeAll() {
        region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        s3VectorsClient = S3VectorsClient.builder().region(Region.of(region)).build();

        bucketName = System.getenv("S3_VECTORS_BUCKET_NAME");
        if (bucketName == null || bucketName.isBlank()) {
            bucketName = TEST_BUCKET_PREFIX + randomUUID().substring(0, 8);
            s3VectorsClient.createVectorBucket(CreateVectorBucketRequest.builder()
                    .vectorBucketName(bucketName)
                    .build());
            bucketCreatedByTest = true;
        }
    }

    @AfterAll
    static void afterAll() {
        if (s3VectorsClient == null) {
            return;
        }
        if (bucketCreatedByTest) {
            try {
                s3VectorsClient.deleteVectorBucket(DeleteVectorBucketRequest.builder()
                        .vectorBucketName(bucketName)
                        .build());
            } catch (Exception e) {
                // best-effort cleanup
            }
        }
        s3VectorsClient.close();
    }

    @BeforeEach
    void setIndexName() {
        indexName = TEST_INDEX_PREFIX + randomUUID().substring(0, 8);
    }

    @AfterEach
    void deleteIndex() {
        try {
            s3VectorsClient.deleteIndex(DeleteIndexRequest.builder()
                    .vectorBucketName(bucketName)
                    .indexName(indexName)
                    .build());
        } catch (Exception e) {
            // index may not have been created by the test
        }
    }

    @Test
    void should_respect_embedding_model_bean() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(TestEmbeddingModelAutoConfiguration.class))
                .withPropertyValues(properties())
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel)
                            .isNotNull()
                            .isExactlyInstanceOf(AllMiniLmL6V2QuantizedEmbeddingModel.class);
                    S3VectorsEmbeddingStore embeddingStore = context.getBean(S3VectorsEmbeddingStore.class);
                    assertThat(embeddingStore).isNotNull();
                });
    }

    @Override
    protected Class<?> autoConfigurationClass() {
        return S3VectorsEmbeddingStoreAutoConfiguration.class;
    }

    @Override
    protected Class<? extends EmbeddingStore<TextSegment>> embeddingStoreClass() {
        return S3VectorsEmbeddingStore.class;
    }

    @Override
    protected String[] properties() {
        return new String[] {
            "langchain4j.community.s3-vectors.vector-bucket-name=" + bucketName,
            "langchain4j.community.s3-vectors.index-name=" + indexName,
            "langchain4j.community.s3-vectors.region=" + region,
            "langchain4j.community.s3-vectors.create-index-if-not-exists=true"
        };
    }

    @Override
    protected String dimensionPropertyKey() {
        // S3 Vectors derives the dimension from the first stored vector; no such property exists,
        // so this key binds to nothing and is ignored.
        return "langchain4j.community.s3-vectors.dimension";
    }

    @Override
    protected boolean assertEmbedding() {
        // S3 Vectors does not return the stored vector on query.
        return false;
    }

    @Override
    protected void awaitUntilPersisted(ApplicationContext context) {
        // S3 Vectors can lag between PutVectors and QueryVectors, and the base test searches only once.
        Embedding probe =
                new AllMiniLmL6V2QuantizedEmbeddingModel().embed("hello").content();
        S3VectorsEmbeddingStore embeddingStore = context.getBean(S3VectorsEmbeddingStore.class);
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(embeddingStore
                                .search(EmbeddingSearchRequest.builder()
                                        .queryEmbedding(probe)
                                        .maxResults(10)
                                        .build())
                                .matches())
                        .isNotEmpty());
    }
}
