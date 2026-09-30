package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class XinferenceToolChatModelIT extends AbstractXinferenceToolsChatModelInfrastructure {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static final String CALL_ONE_TOOL_AT_A_TIME =
            " STRICT RULE: you must call EXACTLY ONE tool per turn. Do NOT call any other tool until you have received"
                    + " the result of the previous tool call. Calling more than one tool in the same turn is FORBIDDEN.";

    static final String ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER =
            " Always use available tools to calculate the answer.";

    ToolSpecification weatherToolSpecification = ToolSpecification.builder()
            .name("get_current_weather")
            .description("Fetches the current weather of a specific city")
            .parameters(JsonObjectSchema.builder()
                    .addEnumProperty(
                            "format",
                            List.of("celsius", "fahrenheit"),
                            "The format to return the weather in, e.g. 'celsius' or 'fahrenheit'")
                    .addStringProperty("location", "The location to get the weather for, e.g. San Francisco")
                    .required("format", "location")
                    .build())
            .build();

    ToolSpecification toolWithoutParameter = ToolSpecification.builder()
            .name("get_current_time")
            .description("Get the current time")
            .build();

    ChatModel chatModel;

    @BeforeEach
    public void beforeEach() {
        chatModel = XinferenceChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(modelName())
                .apiKey(apiKey())
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected List<ChatModel> models() {
        if (chatModel == null) {
            chatModel = XinferenceChatModel.builder()
                    .baseUrl(baseUrl())
                    .modelName(modelName())
                    .apiKey(apiKey())
                    .temperature(0.0)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }
        return singletonList(chatModel);
    }

    @Test
    void should_execute_a_tool_then_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("What is the weather today in Paris?");
        List<ToolSpecification> toolSpecifications = singletonList(weatherToolSpecification);

        // when
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(singletonList(userMessage))
                .toolSpecifications(toolSpecifications)
                .build());

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_weather");
        assertThat(MAPPER.readTree(toolExecutionRequest.arguments()))
                .isEqualTo(MAPPER.readTree("{\"format\": \"celsius\", \"location\": \"Paris\"}"));

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(
                toolExecutionRequest, "{\"format\": \"celsius\", \"location\": \"Paris\", \"temperature\": \"32\"}");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        ChatResponse secondResponse = chatModel.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_not_execute_a_tool_and_tell_a_joke() {

        // given
        List<ToolSpecification> toolSpecifications = singletonList(weatherToolSpecification);

        // when
        List<ChatMessage> chatMessages = asList(systemMessage("Use tools only if needed"), userMessage("Tell a joke"));
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(chatMessages)
                .toolSpecifications(toolSpecifications)
                .build());

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNotNull();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_handle_tool_without_parameter() {

        // given
        List<ToolSpecification> toolSpecifications = singletonList(toolWithoutParameter);

        // when
        List<ChatMessage> chatMessages = singletonList(userMessage("What is the current time?"));

        // then
        assertDoesNotThrow(() -> {
            chatModel.chat(ChatRequest.builder()
                    .messages(chatMessages)
                    .toolSpecifications(toolSpecifications)
                    .build());
        });
    }

    @Override
    @Disabled("Qwen can't do this reliably at 2B.")
    protected void should_execute_tool_with_list_of_POJOs_parameter(ChatModel chatModel) {
        // The prompt is somewhat vague ('Process the following people: Klaus and Franny'),
        // causing the test to be quite flaky because Qwen doesn't always use the 'process' tool.
    }

    @Override
    protected boolean supportsMapParameters() {
        // Almost the same issue as the test above, only that the prompt is even more vague,
        // causing this test to almost always fail.
        return false;
    }

    @Override
    public String adaptPrompt1(String prompt) {
        return prompt + CALL_ONE_TOOL_AT_A_TIME + ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER;
    }

    @Override
    public String adaptPrompt2(String prompt) {
        return prompt + ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER;
    }

    @Override
    public String adaptPrompt3(String prompt) {
        return prompt + ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER;
    }
}
