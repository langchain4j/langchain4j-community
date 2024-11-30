package dev.langchain4j.community.model.xinference;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

class XinferenceStreamingLanguageModelIT extends AbstractInferenceLanguageModelInfrastructure {

    StreamingLanguageModel model = XinferenceStreamingLanguageModel.builder()
            .baseUrl(baseUrl())
            .apiKey(apiKey())
            .modelName(modelName())
            .temperature(0.5)
            .logRequests(true)
            .logResponses(true)
            .maxTokens(20)
            .timeout(Duration.ofSeconds(60))
            .build();

    @Test
    void should_stream_answer() throws Exception {
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate("中国首都是哪里？", handler);
        Response<String> response = handler.get();
        assertThat(response.content()).contains("北京");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
