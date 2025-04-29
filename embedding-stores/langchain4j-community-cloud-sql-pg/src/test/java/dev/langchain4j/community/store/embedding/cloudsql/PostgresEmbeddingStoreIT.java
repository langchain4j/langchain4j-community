package dev.langchain4j.community.store.embedding.cloudsql;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.community.store.embedding.cloudsql.index.DistanceStrategy;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresEmbeddingStoreIT extends EmbeddingStoreIT {

    // Does support WithFilteringIT but can not handle different age data types.

    @Container
    static PostgreSQLContainer<?> pgVector =
            new PostgreSQLContainer<>("pgvector/pgvector:pg15").withCommand("postgres -c max_connections=100");

    final String tableName = "test" + nextInt(2000, 3000);
    static PostgresEngine engine;
    EmbeddingStore<TextSegment> embeddingStore;
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void startEngine() {
        if (engine == null) {
            engine = new PostgresEngine.Builder()
                    .host(pgVector.getHost())
                    .port(pgVector.getFirstMappedPort())
                    .user("test")
                    .password("test")
                    .database("test")
                    .build();
        }
    }

    @AfterAll
    static void stopEngine() {
        engine.close();
    }

    @Override
    protected void ensureStoreIsReady() {
        List<MetadataColumn> metadataColumns = new ArrayList<>();
        metadataColumns.add(new MetadataColumn("name", "text", true));
        metadataColumns.add(new MetadataColumn("name2", "text", true));
        metadataColumns.add(new MetadataColumn("city", "text", true));
        metadataColumns.add(new MetadataColumn("age", "integer", true));

        engine.initVectorStoreTable(new EmbeddingStoreConfig.Builder(tableName, 384)
                .metadataColumns(metadataColumns)
                .overwriteExisting(true)
                .build());

        List<String> metadataColumnNames =
                metadataColumns.stream().map(c -> c.getName()).collect(Collectors.toList());

        embeddingStore = new PostgresEmbeddingStore.Builder(engine, tableName)
                .distanceStrategy(DistanceStrategy.COSINE_DISTANCE)
                .metadataColumns(metadataColumnNames)
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        if (embeddingStore == null) {
            final int MAX_ATTEMPTS = 3;
            int attempt = 0;

            while (attempt < MAX_ATTEMPTS) {
                attempt++;
                try {
                    ensureStoreIsReady();
                    break;
                } catch (RuntimeException e) {
                    System.err.println("Attempt " + attempt + ": RuntimeException caught: " + e.getMessage());
                    try {
                        Thread.sleep(1000); // Sleep for 1 second (1000 milliseconds)
                    } catch (InterruptedException ie) {
                        System.err.println("InterruptedException while sleeping: " + ie.getMessage());
                        break; // Exit the loop if interrupted
                    }
                }
            }
        }
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
