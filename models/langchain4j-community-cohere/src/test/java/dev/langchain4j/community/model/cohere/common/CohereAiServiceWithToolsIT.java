package dev.langchain4j.community.model.cohere.common;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    private static final ChatModel COHERE_CHAT_MODEL = CohereChatModel.builder()
            .modelName("command-r7b-12-2024")
            .apiKey(System.getenv("CO_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final ChatModel LARGER_COHERE_CHAT_MODEL = CohereChatModel.builder()
            .modelName("command-r-plus-08-2024")
            .apiKey(System.getenv("CO_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(COHERE_CHAT_MODEL);
    }

    @Override
    protected void should_execute_tool_with_list_of_POJOs_parameter(ChatModel chatModel) {
        // Smaller Cohere models 'flatten' tool parameters into single lists, causing
        // this test to fail.
        super.should_execute_tool_with_list_of_POJOs_parameter(LARGER_COHERE_CHAT_MODEL);
    }

    // TODO: should_execute_normal_tool_with_primitive_parameters is a flaky test.
}
