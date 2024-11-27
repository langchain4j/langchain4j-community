package dev.langchain4j.community.model.xinference;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "XINFERENCE_BASE_URL", matches = ".+")
class XinferenceStreamingLanguageModelIT extends AbstractModelInfrastructure {

    StreamingLanguageModel model = XinferenceStreamingLanguageModel.builder()
            .baseUrl(XINFERENCE_BASE_URL)
            .modelName(LANGUAGE_MODEL_NAME)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer() throws Exception {

        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();

        model.generate("中国首都是哪座城市?", handler);
        Response<String> response = handler.get();

        assertThat(response.content()).contains("北京");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
