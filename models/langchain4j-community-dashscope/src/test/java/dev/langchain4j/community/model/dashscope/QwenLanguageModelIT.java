package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenLanguageModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#languageModelNameProvider")
    void should_send_messages_and_receive_response(String modelName) {
        LanguageModel model = QwenLanguageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        Response<String> response = model.generate("Please say 'hello' to me");

        assertThat(response.content()).containsIgnoringCase("hello");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#languageModelNameProvider")
    void should_send_messages_and_receive_response_by_customized_request(String modelName) {
        QwenLanguageModel model = QwenLanguageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        Response<String> response = model.generate("Please say 'hello' to me");

        model.setGenerationParamCustomizer(generationParamBuilder -> generationParamBuilder.stopString("hello"));

        // it should generate "hello" but is stopped
        assertThat(response.content()).doesNotContain("hello");
    }
}
