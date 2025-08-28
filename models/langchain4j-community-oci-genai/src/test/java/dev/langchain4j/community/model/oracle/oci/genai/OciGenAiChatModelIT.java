package dev.langchain4j.community.model.oracle.oci.genai;

import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.NON_EMPTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_CHAT_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_VISION_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION_PROPERTY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_MODEL_REGION_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
})
public class OciGenAiChatModelIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(OciGenAiChatModelIT.class);

    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();

    @Test
    void squareRoot() {
        try (var chatModel = OciGenAiChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .maxTokens(600)
                .temperature(0.2)
                .topP(0.75)
                .seed(TestEnvProps.SEED)
                .build()) {

            var mathService = new MathService();

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .tools(mathService)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            assertThat(
                    assistant.chat("Calculate square root of 16."),
                    either(containsString("4")).or(containsStringIgnoringCase("four")));

            assertThat(mathService.results, hasItem(4.0));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
    void image() {
        var bytes = Utils.readBytes("https://www.gstatic.com/webp/gallery3/2.png");
        var base64image = Base64.getEncoder().encodeToString(bytes);

        try (var chatModel = OciGenAiChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_VISION_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .build()) {

            // Only url encoded base64 is supported
            ImageContent imageContent = ImageContent.from(base64image, "image/png");
            UserMessage userMessage = UserMessage.from(imageContent);

            ChatResponse response = chatModel.chat(
                    UserMessage.userMessage("Can you describe what text is on the picture?"), userMessage);

            Assertions.assertThat(response.aiMessage().text()).containsIgnoringCase("penguin");
        }
    }

    @Test
    void squareRootCohere() {
        try (var chatModel = OciGenAiCohereChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .maxTokens(600)
                .temperature(0.2)
                .topP(0.75)
                .build()) {

            var mathService = new MathService();

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .tools(mathService)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            assertThat(assistant.chat("Calculate square root of 16 and return the result."), containsString("four"));

            assertThat(mathService.results, hasItem(4.0));
        }
    }

    @Test
    void streamingChat() throws ExecutionException, InterruptedException, TimeoutException {

        try (var chatModel = OciGenAiStreamingChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .maxTokens(600)
                .temperature(0.2)
                .topP(0.75)
                .build()) {

            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            var cf = new CompletableFuture<ChatResponse>();
            List<String> partialResult = new ArrayList<>();

            assistant
                    .chat("Can you tell 2 jokes each containing one day of weekend?")
                    .onPartialResponse(partialResult::add)
                    .onCompleteResponse(cf::complete)
                    .onError(cf::completeExceptionally)
                    .start();

            cf.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            assertThat(partialResult.toString(), partialResult.size(), greaterThan(2));
            var result = String.join("", partialResult);
            assertThat(result, containsString("SATURDAY"));
            assertThat(result, containsString("SUNDAY"));
        }
    }

    @Test
    void streamingCohereChat() throws ExecutionException, InterruptedException, TimeoutException {

        try (var chatModel = OciGenAiCohereStreamingChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .maxTokens(600)
                .temperature(0.2)
                .topP(0.75)
                .build()) {

            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            var cf = new CompletableFuture<ChatResponse>();
            List<String> partialResult = new ArrayList<>();

            assistant
                    .chat("Can you make 2 sentences each containing one day of weekend?")
                    .onPartialResponse(partialResult::add)
                    .onCompleteResponse(cf::complete)
                    .onError(cf::completeExceptionally)
                    .start();

            cf.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            assertThat(partialResult.size(), greaterThan(2));
            var result = String.join("", partialResult);
            assertThat(result, containsString("SATURDAY"));
            assertThat(result, containsString("SUNDAY"));
        }
    }

    @Test
    void streamingChatWithTools() throws ExecutionException, InterruptedException, TimeoutException {

        try (var chatModel = OciGenAiStreamingChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .build()) {

            var assistant = AiServices.builder(NoSystemMessageAssistant.class)
                    .streamingChatModel(chatModel)
                    .tools(new MathService())
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            var cf = new CompletableFuture<ChatResponse>();
            List<String> partialResult = new ArrayList<>();

            assistant
                    .chat("Calculate square root of 16.")
                    .onPartialResponse(partialResult::add)
                    .onCompleteResponse(cf::complete)
                    .onError(cf::completeExceptionally)
                    .start();

            LOGGER.debug(
                    "Complete response: {}",
                    cf.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                            .aiMessage()
                            .text());

            assertThat(partialResult.size(), greaterThan(2));
            var result = String.join("", partialResult);
            assertThat(result, containsString("4.0"));
        }
    }

    @Test
    void streamingCohereChatWithTools() throws ExecutionException, InterruptedException, TimeoutException {

        try (var chatModel = OciGenAiCohereStreamingChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .maxTokens(600)
                .temperature(0.2)
                .topP(0.75)
                .seed(TestEnvProps.SEED)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .build()) {

            var mathService = new MathService();

            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(chatModel)
                    .tools(mathService)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            var partialResults = new CopyOnWriteArrayList<String>();
            var future = new CompletableFuture<ChatResponse>();

            assistant
                    .chat("Calculate square root of 16 and don't forget to end the sentence with period.")
                    .onPartialResponse(s -> {
                        if (mathService.future.isDone()) {
                            partialResults.add(String.format("%s", s.replaceAll("\\\\n", "\n")));
                        }
                    })
                    .onCompleteResponse(future::complete)
                    .onError(Assertions::fail)
                    .start();

            var completeResponse = future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            var joinedResult = String.join("", partialResults);
            assertThat(joinedResult, is(completeResponse.aiMessage().text()));
            assertThat(mathService.results, hasItem(4.0));
            assertThat(mathService.callCounter.get(), is(1));
        }
    }

    public interface NoSystemMessageAssistant {
        TokenStream chat(String text);
    }

    public interface Assistant {
        @SystemMessage("You always answer numerical results by words")
        String chat(String text);
    }

    public interface StreamingAssistant {

        @SystemMessage("You always answer all in uppercase")
        TokenStream chat(String text);
    }

    public static class MathService {

        final List<Double> results = new ArrayList<>();
        final AtomicInteger callCounter = new AtomicInteger();
        final CompletableFuture<List<Double>> future = new CompletableFuture<>();

        @Tool("Calculates the square root of a number and returns actual result.")
        double sqrt(@P("number") Integer number) {
            callCounter.incrementAndGet();
            LOGGER.debug("Calculating {} square root", number);
            var result = -1.0;
            if (number != null) {
                result = Math.sqrt(number);
            }
            results.add(result);
            future.complete(results);
            return result;
        }
    }
}
