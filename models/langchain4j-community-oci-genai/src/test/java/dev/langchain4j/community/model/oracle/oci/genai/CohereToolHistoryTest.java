package dev.langchain4j.community.model.oracle.oci.genai;

import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.NON_EMPTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION_PROPERTY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.EmbedTextDetails;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.requests.EmbedTextRequest;
import com.oracle.bmc.generativeaiinference.responses.EmbedTextResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_MODEL_REGION_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
})
public class CohereToolHistoryTest {

    static final Logger LOGGER = LoggerFactory.getLogger(CohereToolHistoryTest.class);
    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();
    static final List<List<Float>> EMBEDDINGS = new ArrayList<>();
    static final AtomicInteger EMBEDDINGS_SEQ = new AtomicInteger(0);

    @Test
    public void sequentialToolCalls() throws ExecutionException, InterruptedException, TimeoutException {
        try (var model = OciGenAiCohereChatModel.builder()
                .modelName(TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(TestEnvProps.OCI_GENAI_COMPARTMENT_ID)
                .region(Region.fromRegionCodeOrId(TestEnvProps.OCI_GENAI_MODEL_REGION))
                .authProvider(authProvider)
                .temperature(0.3)
                .seed(TestEnvProps.SEED)
                .maxTokens(4000)
                .build()) {

            var tools = new TestTools();

            var embeddingAiService = AiServices.builder(TestEmbeddingAiService.class)
                    .tools(tools)
                    .toolExecutionErrorHandler((throwable, context) -> fail(throwable))
                    .chatModel(model)
                    .build();

            var result = embeddingAiService.embed("It's bucketing down", "It's raining cats and dogs");
            LOGGER.info("Result> {}", result.content());

            assertThat(
                    result.toolExecutions().stream()
                            .map(ToolExecution::request)
                            .map(ToolExecutionRequest::name)
                            .toList(),
                    contains("storeEmbedding", "storeEmbedding", "calculateCosineSimilarity"));

            assertThat(result.content(), containsString(String.valueOf(tools.similarity.get(10, TimeUnit.SECONDS))));
        }
    }

    interface TestEmbeddingAiService {

        @SystemMessage(
                """
                You must use provided tool to calculate cosine similarity between the two embedding ids.
                You must never calculate cosine similarity yourself, always use tool.
                """)
        @UserMessage(
                """
                Store embeddings of following two strings "{{firstEmbedString}}", "{{secondEmbedString}}"..
                When you have two resulting embedding ids use them to calculate cosine similarity, use tool for that.
                """)
        Result<String> embed(
                @V("firstEmbedString") String firstEmbedString, @V("secondEmbedString") String secondEmbedString);
    }

    static class TestTools {

        CompletableFuture<Double> similarity = new CompletableFuture<>();
        EmbeddingClient embeddingClient = new EmbeddingClient();

        @Tool("Store embedding of an input in the embedding database. Return the result id of the stored embedding.")
        int storeEmbedding(@P("String input for embeddings") String input) {
            LOGGER.info("Storing embedding \"{}\"", input);
            var nextId = EMBEDDINGS_SEQ.getAndIncrement();
            EMBEDDINGS.add(nextId, embeddingClient.getEmbeddings(List.of(input)).get(0));
            return nextId;
        }

        @Tool("Calculate cosine similarity between the two embeddings identified by provided ids.")
        double calculateCosineSimilarity(@P("First embedding id") int id1, @P("Second embedding id") int id2) {
            LOGGER.info("Computing similarity id1={} id2={}", id1, id2);
            var similarity = getCosineSimilarity(EMBEDDINGS.get(id1), EMBEDDINGS.get(id2));
            LOGGER.info("Computed similarity is {}", similarity);
            this.similarity.complete(similarity);
            return similarity;
        }

        public static double[] getL2Normed(List<Float> vector) {
            var norm = (float) Math.sqrt(vector.stream().mapToDouble(e -> e * e).sum());
            return vector.stream().mapToDouble(e -> e / norm).toArray();
        }

        public static double getCosineSimilarity(List<Float> vector1, List<Float> vector2) {
            if (vector1.size() != vector2.size()) throw new RuntimeException("Vectors are having different size");

            var vector1Normed = getL2Normed(vector1);
            var vector2Normed = getL2Normed(vector2);

            return IntStream.range(0, vector1.size())
                    .mapToDouble(i -> vector1Normed[i] * vector2Normed[i])
                    .sum();
        }
    }

    public static class EmbeddingClient {
        public EmbeddingClient() {}

        public List<List<Float>> getEmbeddings(List<String> input) {
            var clientBuilder = GenerativeAiInferenceClient.builder()
                    .region(Region.fromRegionCodeOrId(TestEnvProps.OCI_GENAI_MODEL_REGION));

            try (var embedClient = clientBuilder.build(authProvider)) {
                EmbedTextDetails embedTextDetails = EmbedTextDetails.builder()
                        .inputs(input)
                        .compartmentId(TestEnvProps.OCI_GENAI_COMPARTMENT_ID)
                        .servingMode(OnDemandServingMode.builder()
                                .modelId("cohere.embed-v4.0")
                                .build())
                        .build();

                EmbedTextRequest request = EmbedTextRequest.builder()
                        .embedTextDetails(embedTextDetails)
                        .build();
                EmbedTextResponse response = embedClient.embedText(request);
                return response.getEmbedTextResult().getEmbeddings();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
