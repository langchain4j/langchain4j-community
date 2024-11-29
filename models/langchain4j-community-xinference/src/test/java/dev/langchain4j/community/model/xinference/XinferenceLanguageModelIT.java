package dev.langchain4j.community.model.xinference;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

class XinferenceLanguageModelIT extends AbstractInferenceLanguageModelInfrastructure {

    LanguageModel model = XinferenceLanguageModel.builder()
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
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {
        String prompt = "中国首都是哪里？";
        Response<String> response = model.generate(prompt);
        assertThat(response.content()).contains("北京");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
