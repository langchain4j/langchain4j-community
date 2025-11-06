package dev.langchain4j.community.model.oracle.oci.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.oracle.bmc.Region;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.ChatResult;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.CohereChatResponse;
import com.oracle.bmc.generativeaiinference.model.GenericChatRequest;
import com.oracle.bmc.generativeaiinference.model.GenericChatResponse;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.generativeaiinference.model.UserMessage;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;
import com.oracle.bmc.generativeaiinference.responses.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConfigurationTest {

    @Test
    void requiredProperties() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            try (var model = OciGenAiChatModel.builder().build()) {}
        });
    }

    @Test
    void cohere() throws Exception {
        GenerativeAiInferenceClient client = Mockito.mock(GenerativeAiInferenceClient.class);

        var builder = OciGenAiCohereChatModel.builder()
                .genAiClient(client)
                .modelName("chatModelId")
                .compartmentId("compartmentId")
                .citationQuality(CohereChatRequest.CitationQuality.Fast)
                .documents(List.of("documents"))
                .frequencyPenalty(0.007)
                .isRawPrompting(true)
                .isSearchQueriesOnly(true)
                .maxInputTokens(999)
                .preambleOverride("preambleOverride")
                .promptTruncation(CohereChatRequest.PromptTruncation.Off)
                .maxTokens(666)
                .presencePenalty(0.006)
                .region(Region.AP_TOKYO_1)
                .seed(123456789)
                .stop(List.of("stop"))
                .temperature(1.1)
                .topK(11)
                .topP(11.11);

        Mockito.when(client.chat(Mockito.any())).thenAnswer(mock -> {
            ChatRequest req = mock.getArgument(0);
            var chatDetails = req.getChatDetails();
            CohereChatRequest cohereReq = (CohereChatRequest) chatDetails.getChatRequest();

            assertThat(chatDetails.getCompartmentId()).isEqualTo("compartmentId");
            assertThat(cohereReq.getCitationQuality()).isEqualTo(CohereChatRequest.CitationQuality.Fast);
            assertThat(cohereReq.getDocuments()).containsExactly("documents");
            assertThat(cohereReq.getFrequencyPenalty()).isEqualTo(0.007);
            assertThat(cohereReq.getIsRawPrompting()).isTrue();
            assertThat(cohereReq.getIsSearchQueriesOnly()).isTrue();
            assertThat(cohereReq.getMaxInputTokens()).isEqualTo(999);
            assertThat(cohereReq.getPreambleOverride()).isEqualTo("preambleOverride");
            assertThat(cohereReq.getPromptTruncation()).isEqualTo(CohereChatRequest.PromptTruncation.Off);
            assertThat(cohereReq.getMaxTokens()).isEqualTo(666);
            assertThat(cohereReq.getPresencePenalty()).isEqualTo(0.006);
            assertThat(cohereReq.getSeed()).isEqualTo(123456789);
            assertThat(cohereReq.getStopSequences()).containsExactly("stop");
            assertThat(cohereReq.getTemperature()).isEqualTo(1.1);
            assertThat(cohereReq.getTopK()).isEqualTo(11);
            assertThat(cohereReq.getTopP()).isEqualTo(11.11);

            return ChatResponse.builder()
                    .chatResult(ChatResult.builder()
                            .chatResponse(
                                    CohereChatResponse.builder().text("Huh!").build())
                            .build())
                    .build();
        });

        try (var model = builder.build()) {
            assertThat(model.chat("BAF!")).isEqualTo("Huh!");
        }
    }

    @Test
    void generic() throws Exception {
        GenerativeAiInferenceClient client = Mockito.mock(GenerativeAiInferenceClient.class);

        var builder = OciGenAiChatModel.builder()
                .genAiClient(client)
                .modelName("chatModelId")
                .compartmentId("compartmentId")
                .frequencyPenalty(0.007)
                .logitBias(0.008)
                .numGenerations(33)
                .maxTokens(666)
                .presencePenalty(0.006)
                .region(Region.AP_TOKYO_1)
                .seed(123456789)
                .stop(List.of("stop"))
                .temperature(1.1)
                .topK(11)
                .topP(11.11);

        Mockito.when(client.chat(Mockito.any())).thenAnswer(mock -> {
            ChatRequest req = mock.getArgument(0);
            var chatDetails = req.getChatDetails();
            GenericChatRequest cohereReq = (GenericChatRequest) chatDetails.getChatRequest();

            assertThat(chatDetails.getCompartmentId()).isEqualTo("compartmentId");
            assertThat(cohereReq.getLogitBias()).isEqualTo(0.008);
            assertThat(cohereReq.getNumGenerations()).isEqualTo(33);
            assertThat(cohereReq.getFrequencyPenalty()).isEqualTo(0.007);
            assertThat(cohereReq.getMaxTokens()).isEqualTo(666);
            assertThat(cohereReq.getPresencePenalty()).isEqualTo(0.006);
            assertThat(cohereReq.getSeed()).isEqualTo(123456789);
            assertThat(cohereReq.getStop()).containsExactly("stop");
            assertThat(cohereReq.getTemperature()).isEqualTo(1.1);
            assertThat(cohereReq.getTopK()).isEqualTo(11);
            assertThat(cohereReq.getTopP()).isEqualTo(11.11);

            return ChatResponse.builder()
                    .chatResult(ChatResult.builder()
                            .chatResponse(GenericChatResponse.builder()
                                    .choices(List.of(ChatChoice.builder()
                                            .message(UserMessage.builder()
                                                    .content(List.of(TextContent.builder()
                                                            .text("Huh!")
                                                            .build()))
                                                    .name("test-name")
                                                    .build())
                                            .finishReason("stop")
                                            .build()))
                                    .build())
                            .build())
                    .build();
        });

        try (var model = builder.build()) {
            assertThat(model.chat("BAF!")).isEqualTo("Huh!");
            var userMessage = dev.langchain4j.data.message.UserMessage.userMessage("BAF!");
            var response = model.chat(userMessage);
            assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        }
    }
}
