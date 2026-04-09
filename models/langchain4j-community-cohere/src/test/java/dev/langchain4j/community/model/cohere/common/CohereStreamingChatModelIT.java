package dev.langchain4j.community.model.cohere.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final StreamingChatModel COHERE_STREAMING_CHAT_MODEL = CohereStreamingChatModel.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("command-r7b-12-2024")
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final StreamingChatModel COHERE_STREAMING_VISION_MODEL = CohereStreamingChatModel.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("command-a-vision-07-2025")
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    public StreamingChatModel createModelWith(ChatModelListener chatModelListener) {
        return CohereStreamingChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName("command-r7b-12-2024")
                .listeners(List.of(chatModelListener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public StreamingChatModel createModelWith(ChatRequestParameters chatRequestParameters) {
        return CohereStreamingChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName("command-r-plus-08-2024")
                .defaultRequestParameters(chatRequestParameters)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    public String customModelName() {
        return "command-r-plus-08-2024";
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(COHERE_STREAMING_CHAT_MODEL);
    }

    @Override
    public List<StreamingChatModel> modelsSupportingImageInputs() {
        return List.of(COHERE_STREAMING_VISION_MODEL);
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeastOnce()).onPartialToolCall(any(), any());

        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id)
                    && request.name().equals("getWeather")
                    && request.arguments().equals("{\"city\": \"Munich\"}");
        }));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler, atLeastOnce())
                .onPartialToolCall(
                        argThat(toolCall -> toolCall.index() == 0
                                && toolCall.id().equals(id1)
                                && toolCall.name().equals("getWeather")
                                && !toolCall.partialArguments().isBlank()),
                        any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id1)
                    && request.name().equals("getWeather")
                    && request.arguments().equals("{\"city\": \"Munich\"}");
        }));

        io.verify(handler, atLeastOnce())
                .onPartialToolCall(
                        argThat(toolCall -> toolCall.index() == 1
                                && toolCall.id().equals(id2)
                                && toolCall.name().equals("getTime")
                                && !toolCall.partialArguments().isBlank()),
                        any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 1
                    && request.id().equals(id2)
                    && request.name().equals("getTime")
                    && request.arguments().equals("{\"country\": \"France\"}");
        }));
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
        return CohereChatResponseMetadata.class;
    }
}
