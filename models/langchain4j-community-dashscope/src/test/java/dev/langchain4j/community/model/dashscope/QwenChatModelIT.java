package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_MAX;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.functionCallChatModelNameProvider;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithAudioData;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithAudioUrl;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithImageData;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithImageUrl;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithVideoData;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.multimodalChatMessagesWithVideoUrl;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.nonMultimodalChatModelNameProvider;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.vlChatModelNameProvider;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenChatModelIT extends AbstractChatModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#nonMultimodalChatModelNameProvider")
    void should_send_non_multimodal_messages_and_receive_response(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(QwenTestHelper.chatMessages());

        assertThat(response.aiMessage().text()).containsIgnoringCase("rain");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#nonMultimodalChatModelNameProvider")
    void should_send_non_multimodal_messages_and_receive_response_by_customized_request(String modelName) {
        QwenChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        model.setGenerationParamCustomizer(
                generationParamBuilder -> generationParamBuilder.stopStrings(List.of("Rainy", "rainy")));

        ChatResponse response = model.chat(QwenTestHelper.chatMessages());

        // it should generate "rain" but is stopped
        assertThat(response.aiMessage().text()).doesNotContainIgnoringCase("rain");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_function_with_no_argument_then_answer(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        String toolName = "getCurrentDateAndTime";
        ToolSpecification noArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Get the current date and time")
                .build();

        UserMessage userMessage = UserMessage.from("What time is it?");

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(singletonList(noArgToolSpec))
                .build());

        assertThat(response.aiMessage().text()).isBlank();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                response.aiMessage().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).isEqualTo("{}");
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "10 o'clock");
        List<ChatMessage> messages = asList(userMessage, response.aiMessage(), toolExecutionResultMessage);

        ChatResponse secondResponse = model.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(singletonList(noArgToolSpec))
                .build());

        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("10");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

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
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        String toolName = "getCurrentWeather";
        ToolSpecification hasArgToolSpec = ToolSpecification.builder()
                .name(toolName)
                .description("Query the weather of a specified city")
                .parameters(
                        JsonObjectSchema.builder().addStringProperty("cityName").build())
                .build();

        UserMessage userMessage = UserMessage.from("Weather in Beijing?");

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(singletonList(hasArgToolSpec))
                .build());

        assertThat(response.aiMessage().text()).isBlank();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                response.aiMessage().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).contains("Beijing");
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "rainy");
        List<ChatMessage> messages = asList(userMessage, response.aiMessage(), toolExecutionResultMessage);

        ChatResponse secondResponse = model.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(singletonList(hasArgToolSpec))
                .build());

        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("rain");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

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
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        String toolName = "getCurrentWeather";
        ToolSpecification mustBeExecutedTool = ToolSpecification.builder()
                .name(toolName)
                .description("Query the weather of a specified city")
                .parameters(
                        JsonObjectSchema.builder().addStringProperty("cityName").build())
                .build();

        // not related to tools
        UserMessage userMessage = UserMessage.from("How many students in the classroom?");

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(userMessage)
                .parameters(QwenChatRequestParameters.builder()
                        .toolSpecifications(mustBeExecutedTool)
                        .toolChoice(REQUIRED)
                        .build())
                .build());

        assertThat(response.aiMessage().text()).isBlank();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest =
                response.aiMessage().toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(toolName);
        assertThat(toolExecutionRequest.arguments()).hasSizeGreaterThan(0);
        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_call_must_be_executed_call_function_with_argument_then_answer(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

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

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(singletonList(userMessage))
                .toolSpecifications(calculator)
                .build());

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isBlank();
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

        ChatResponse secondResponse = model.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(singletonList(calculator))
                .build());

        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

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
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(multimodalChatMessagesWithImageUrl());

        assertThat(response.aiMessage().text()).containsIgnoringCase("dog");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    void should_send_multimodal_image_data_and_receive_response(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(multimodalChatMessagesWithImageData());

        assertThat(response.aiMessage().text()).containsIgnoringCase("parrot");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    void should_send_multimodal_audio_url_and_receive_response(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(multimodalChatMessagesWithAudioUrl());

        assertThat(response.aiMessage().text()).containsIgnoringCase("阿里云");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#audioChatModelNameProvider")
    void should_send_multimodal_audio_data_and_receive_response(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(multimodalChatMessagesWithAudioData());

        assertThat(response.aiMessage().text()).containsIgnoringCase("阿里云");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    void should_send_multimodal_video_url_and_receive_response(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(multimodalChatMessagesWithVideoUrl());

        assertThat(response.aiMessage().text()).containsIgnoringCase("parrot");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#vlChatModelNameProvider")
    void should_send_multimodal_video_data_and_receive_response(String modelName) {
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

        ChatResponse response = model.chat(multimodalChatMessagesWithVideoData());

        assertThat(response.aiMessage().text()).containsIgnoringCase("parrot");
    }

    @Test
    void should_sanitize_messages() {
        ToolExecutionRequest toolExecutionRequest =
                ToolExecutionRequest.builder().build();
        List<ChatMessage> messages = new LinkedList<>();

        // 1. System message should be the first message.
        // 2. First non-system message must be a user message.
        // 3. User message should follow a normal system/AI message.
        // 4. Tool execution result should follow a tool execution request message.
        // 5. AI message should follow a user/tool_execution_result message.
        // 6. Last message in the message list should be a user message. This serves as the model query/input for the
        // current round.

        messages.add(SystemMessage.from("System message 1, which should be discarded"));
        messages.add(SystemMessage.from("System message 2"));

        messages.add(AiMessage.from("AI message 1, which should be discarded"));
        messages.add(ToolExecutionResultMessage.from(
                toolExecutionRequest, "Tool execution result 1, which should be discards"));
        messages.add(UserMessage.from("User message 1, which should be discarded"));
        messages.add(UserMessage.from("User message 2"));

        messages.add(AiMessage.from("AI message 2, which should be discarded"));
        messages.add(AiMessage.from(
                "AI message 3, a tool execution request", Collections.singletonList(toolExecutionRequest)));

        messages.add(ToolExecutionResultMessage.from(
                toolExecutionRequest, "Tool execution result 2, which should be discards"));
        messages.add(ToolExecutionResultMessage.from(toolExecutionRequest, "Tool execution result 3"));

        messages.add(AiMessage.from("AI message 4"));

        messages.add(UserMessage.from("User message 5, which should be discards"));
        messages.add(AiMessage.from(
                "AI message 5, a tool execution request,"
                        + " which can't be followed by a user message, should be discards",
                Collections.singletonList(toolExecutionRequest)));
        messages.add(UserMessage.from("User message 6"));

        messages.add(AiMessage.from("AI message 6, which should be discards"));

        // The result should be in the following order:
        // 1. System message
        // 2. User message
        // 3. AI message
        // 4. Tool execution result message
        // 5. AI message
        // 6. User message
        List<ChatMessage> sanitizedMessages = QwenHelper.sanitizeMessages(messages);
        assertThat(sanitizedMessages).hasSize(6);

        assertThat(sanitizedMessages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sanitizedMessages.get(0)).text()).isEqualTo("System message 2");

        assertThat(sanitizedMessages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitizedMessages.get(1)).singleText()).isEqualTo("User message 2");

        assertThat(sanitizedMessages.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitizedMessages.get(2)).text()).isEqualTo("AI message 3, a tool execution request");

        assertThat(sanitizedMessages.get(3)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) sanitizedMessages.get(3)).text())
                .isEqualTo("Tool execution result 3");

        assertThat(sanitizedMessages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) sanitizedMessages.get(4)).text()).isEqualTo("AI message 4");

        assertThat(sanitizedMessages.get(5)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) sanitizedMessages.get(5)).singleText()).isEqualTo("User message 6");
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#functionCallChatModelNameProvider")
    void should_send_messages_and_receive_response_by_searching(String modelName) {
        // given
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

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
                .messages(UserMessage.from("What is the weather of Beijing?"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse).isNotNull();
        assertThat(chatResponse.metadata()).isNotNull();
        assertThat(chatResponse.metadata()).isInstanceOf(QwenChatResponseMetadata.class);

        QwenChatResponseMetadata metadata = (QwenChatResponseMetadata) chatResponse.metadata();
        assertThat(metadata.searchInfo()).isNotNull();
        assertThat(metadata.searchInfo().searchResults()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#mtChatModelNameProvider")
    void should_translate_messages_and_receive_response(String modelName) {
        // given
        ChatModel model =
                QwenChatModel.builder().apiKey(apiKey()).modelName(modelName).build();

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

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse).isNotNull();
        assertThat(chatResponse.aiMessage().text().trim()).isEqualTo("我的内存");
    }

    @Override
    protected List<ChatModel> models() {
        return nonMultimodalChatModelNameProvider()
                .map(Arguments::get)
                .map(modelNames -> modelNames[0])
                .map(modelName -> QwenChatModel.builder()
                        .apiKey(apiKey())
                        .modelName((String) modelName)
                        .temperature(0.0f)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected List<ChatModel> modelsSupportingTools() {
        return functionCallChatModelNameProvider()
                .map(Arguments::get)
                .map(modelNames -> modelNames[0])
                .map(modelName -> QwenChatModel.builder()
                        .apiKey(apiKey())
                        .modelName((String) modelName)
                        .temperature(0.0f)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected List<ChatModel> modelsSupportingStructuredOutputs() {
        return this.modelsSupportingTools();
    }

    @Override
    protected List<ChatModel> modelsSupportingImageInputs() {
        return vlChatModelNameProvider()
                .map(Arguments::get)
                .map(modelNames -> modelNames[0])
                .map(modelName -> QwenChatModel.builder()
                        .apiKey(apiKey())
                        .modelName((String) modelName)
                        .temperature(0.0f)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        QwenChatModel.QwenChatModelBuilder qwenChatModelBuilder =
                QwenChatModel.builder().apiKey(apiKey()).defaultRequestParameters(parameters);
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
    protected String catImageUrl() {
        return "https://cdn.wanx.aliyuncs.com/upload/commons/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    }

    @Override
    protected String diceImageUrl() {
        return "https://cdn.wanx.aliyuncs.com/upload/commons/PNG_transparency_demonstration_1.png";
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return QwenChatResponseMetadata.class;
    }
}
