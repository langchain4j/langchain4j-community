package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenStreamingLanguageModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#languageModelNameProvider")
    void should_send_messages_and_receive_response(String modelName) {
        StreamingLanguageModel model = QwenStreamingLanguageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate("Please say 'hello' to me", handler);
        Response<String> response = handler.get();

        assertThat(response.content()).containsIgnoringCase("hello");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#languageModelNameProvider")
    void should_send_messages_and_receive_response_by_customized_request(String modelName) {
        QwenStreamingLanguageModel model = QwenStreamingLanguageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        model.setGenerationParamCustomizer(generationParamBuilder ->
                generationParamBuilder.stopString("hello"));

        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate("Please say 'hello' to me", handler);
        Response<String> response = handler.get();

        // it should generate "hello" but is stopped
        assertThat(response.content()).doesNotContain("hello");
    }
}
