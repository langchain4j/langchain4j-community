package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.community.model.dashscope.QwenHelper.convertHandler;
import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.chatMessages;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.functionCallChatModelNameProvider;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithAudioData;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithAudioUrl;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithImageData;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithImageUrl;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.nonMultimodalChatModelNameProvider;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.vlChatModelNameProvider;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#nonMultimodalChatModelNameProvider")
    void should_send_non_multimodal_messages_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(chatMessages(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("rain");
        assertThat(response.content().text()).endsWith("That's all!");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#nonMultimodalChatModelNameProvider")
    void should_send_non_multimodal_messages_and_receive_response_by_customized_request(String modelName) {
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        model.setGenerationParamCustomizer(generationParamBuilder -> generationParamBuilder.stopString("rain"));

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(chatMessages(), handler);
        Response<AiMessage> response = handler.get();

        // it should generate "rain" but is stopped
        assertThat(response.content().text()).doesNotContain("rain");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_function_with_no_argument_then_answer(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "getCurrentDateAndTime";
        ToolSpecification noArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current date and time")
                .build();

        UserMessage userMessage = UserMessage.from("What time is it?");

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), singletonList(noArgToolSpec), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "10 o'clock");
        List<ChatMessage> messages = asList(userMessage, response.content(), toolExecutionResultMessage);

        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, singletonList(noArgToolSpec), secondHandler);
        Response<AiMessage> secondResponse = secondHandler.get();

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("10");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isPositive();
        assertThat(secondTokenUsage.outputTokenCount()).isPositive();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_function_with_argument_then_answer(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "getCurrentWeather";
        ToolSpecification hasArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Query the weather of a specified city")
                .parameters(
                        JsonObjectSchema.builder().addStringProperty("cityName").build())
                .build();

        UserMessage userMessage = UserMessage.from("Weather in Beijing?");

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), singletonList(hasArgToolSpec), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).contains("Beijing");
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "rainy");
        List<ChatMessage> messages = asList(userMessage, response.content(), toolExecutionResultMessage);

        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, singletonList(hasArgToolSpec), secondHandler);
        Response<AiMessage> secondResponse = secondHandler.get();

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("rain");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isPositive();
        assertThat(secondTokenUsage.outputTokenCount()).isPositive();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_must_be_executed_call_function(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "getCurrentWeather";
        ToolSpecification mustBeExecutedTool = ToolSpecification.builder()
                .name(toolName)
                .description("Query the weather of a specified city")
                .parameters(
                        JsonObjectSchema.builder().addStringProperty("cityName").build())
                .build();

        // not related to tools
        UserMessage userMessage = UserMessage.from("How many students in the classroom?");

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), mustBeExecutedTool, handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).isNull();
        assertThat(response.content().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                response.content().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).hasSizeGreaterThan(0);
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_must_be_executed_call_function_with_argument_then_answer(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        String toolName = "calculator";
        ToolSpecification calculator = ToolSpecification.builder()
                .name(toolName)
                .description("returns a sum of two numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("first")
                        .addIntegerProperty("second")
                        .build())
                .build();

        UserMessage userMessage = userMessage("2+2=?");

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(singletonList(userMessage), calculator, handler);
        Response<AiMessage> response = handler.get();

        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotNull();
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        TestStreamingResponseHandler<AiMessage> secondHandler = new TestStreamingResponseHandler<>();
        model.generate(messages, singletonList(calculator), secondHandler);
        Response<AiMessage> secondResponse = secondHandler.get();

        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.inputTokenCount()).isPositive();
        assertThat(secondTokenUsage.outputTokenCount()).isPositive();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());
        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    void should_send_multimodal_image_url_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageUrl(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    void should_send_multimodal_image_data_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithImageData(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("parrot");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    void should_send_multimodal_audio_url_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithAudioUrl(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("阿里云");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    void should_send_multimodal_audio_data_and_receive_response(String modelName) {
        StreamingChatLanguageModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        model.generate(multimodalChatMessagesWithAudioData(), handler);
        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("阿里云");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_send_messages_and_receive_response_by_searching(String modelName) {
        // given
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .enableSearch(true)
                .searchOptions(QwenChatRequestParameters.SearchOptions.builder()
                        .citationFormat("[<number>]")
                        .enableCitation(true)
                        .enableSource(true)
                        .forcedSearch(true)
                        .searchStrategy("standard")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(parameters)
                .build();

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        // when
        model.chat(chatRequest, convertHandler(handler));
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response).isNotNull();
        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata()).containsKey("searchInfo");

        QwenChatResponseMetadata.SearchInfo searchInfo =
                (QwenChatResponseMetadata.SearchInfo) response.metadata().get("searchInfo");
        assertThat(searchInfo).isNotNull();
        assertThat(searchInfo.searchResults()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#mtChatModelNameProvider")
    void should_translate_messages_and_receive_response(String modelName) {
        // given
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        QwenChatRequestParameters parameters = QwenChatRequestParameters.builder()
                .translationOptions(QwenChatRequestParameters.TranslationOptions.builder()
                        .sourceLang("English")
                        .targetLang("Chinese")
                        .terms(singletonList(QwenChatRequestParameters.TranslationOptionTerm.builder()
                                .source("memory")
                                .target("内存")
                                .build()))
                        .domains(
                                "The sentence is from Ali Cloud IT domain. It mainly involves computer-related software development and usage methods, including many terms related to computer software and hardware. Pay attention to professional troubleshooting terminologies and sentence patterns when translating. Translate into this IT domain style.")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("my memory"))
                .parameters(parameters)
                .build();

        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        // when
        model.chat(chatRequest, convertHandler(handler));
        Response<AiMessage> response = handler.get();

        // then
        assertThat(response).isNotNull();
        assertThat(response.content().text().trim()).isEqualTo("我的内存");
    }

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return nonMultimodalChatModelNameProvider()
                .map(Arguments::get)
                .map(modelNames -> modelNames[0])
                .map(modelName -> QwenStreamingChatModel.builder()
                        .apiKey(apiKey())
                        .modelName((String) modelName)
                        .temperature(0.0f)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected List<StreamingChatLanguageModel> modelsSupportingTools() {
        return functionCallChatModelNameProvider()
                .map(Arguments::get)
                .map(modelNames -> modelNames[0])
                .map(modelName -> QwenStreamingChatModel.builder()
                        .apiKey(apiKey())
                        .modelName((String) modelName)
                        .temperature(0.0f)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected List<StreamingChatLanguageModel> modelsSupportingStructuredOutputs() {
        return this.modelsSupportingTools();
    }

    @Override
    protected List<StreamingChatLanguageModel> modelsSupportingImageInputs() {
        return vlChatModelNameProvider()
                .map(Arguments::get)
                .map(modelNames -> modelNames[0])
                .map(modelName -> QwenStreamingChatModel.builder()
                        .apiKey(apiKey())
                        .modelName((String) modelName)
                        .temperature(0.0f)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected QwenStreamingChatModel createModelWith(ChatRequestParameters parameters) {
        QwenStreamingChatModel.QwenStreamingChatModelBuilder qwenChatModelBuilder =
                QwenStreamingChatModel.builder().apiKey(apiKey()).defaultRequestParameters(parameters);
        if (parameters.modelName() == null) {
            qwenChatModelBuilder.modelName(QWEN_MAX);
        }
        return qwenChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "qwen-max-2025-01-25";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return QwenChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        // The dashscope service can't access wiki urls...
        return false;
    }
}
