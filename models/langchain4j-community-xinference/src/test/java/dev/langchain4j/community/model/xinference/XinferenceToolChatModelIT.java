package dev.langchain4j.community.model.xinference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class XinferenceToolChatModelIT extends AbstractXinferenceToolsChatModelInfrastructure {

    ToolSpecification weatherToolSpecification = ToolSpecification.builder()
            .name("get_current_weather")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addEnumProperty("format", List.of("celsius", "fahrenheit"), "The format to return the weather in, e.g. 'celsius' or 'fahrenheit'")
                    .addStringProperty("location", "The location to get the weather for, e.g. San Francisco, CA").build()
            )
            .build();

    ToolSpecification toolWithoutParameter = ToolSpecification.builder()
            .name("get_current_time")
            .description("Get the current time")
            .build();

    ChatLanguageModel ollamaChatModel = XinferenceChatModel.builder()
            .baseUrl(baseUrl())
            .modelName(modelName())
            .apiKey(apiKey())
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(ollamaChatModel);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("What is the weather today in Paris?");
        List<ToolSpecification> toolSpecifications = singletonList(weatherToolSpecification);

        // when
        Response<AiMessage> response = ollamaChatModel.generate(singletonList(userMessage), toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_weather");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"format\": \"celsius\", \"location\": \"Paris\"}");

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "{\"format\": \"celsius\", \"location\": \"Paris\", \"temperature\": \"32\"}");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        Response<AiMessage> secondResponse = ollamaChatModel.generate(messages);

        // then
        AiMessage secondAiMessage = secondResponse.content();
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();
    }

    @Test
    void should_not_execute_a_tool_and_tell_a_joke() {

        // given
        List<ToolSpecification> toolSpecifications = singletonList(weatherToolSpecification);

        // when
        List<ChatMessage> chatMessages = asList(
                systemMessage("Use tools only if needed"),
                userMessage("Tell a joke")
        );
        Response<AiMessage> response = ollamaChatModel.generate(chatMessages, toolSpecifications);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNotNull();
        assertThat(aiMessage.toolExecutionRequests()).isNull();
    }

    @Test
    void should_handle_tool_without_parameter() {

        // given
        List<ToolSpecification> toolSpecifications = singletonList(toolWithoutParameter);

        // when
        List<ChatMessage> chatMessages = singletonList(
                userMessage("What is the current time?")
        );

        // then
        assertDoesNotThrow(() -> {
            ollamaChatModel.generate(chatMessages, toolSpecifications);
        });

    }

    @Test
    @Disabled("Not supported yet.")
    @Override
    protected void should_execute_tool_with_pojo_with_primitives() {
        super.should_execute_tool_with_pojo_with_primitives();
    }

    @Test
    @Disabled("The support isn't great, and there are cases where it fails.")
    @Override
    protected void should_execute_tool_with_map_parameter() {
        super.should_execute_tool_with_map_parameter();
    }
}

