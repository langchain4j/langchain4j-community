package dev.langchain4j.community.model.cohere.common;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereChatModelIT extends AbstractChatModelIT {

    private static final ChatModel COHERE_CHAT_MODEL = CohereChatModel.builder()
            .modelName("command-r7b-12-2024")
            .apiKey(System.getenv("CO_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final ChatModel COHERE_VISION_MODEL = CohereChatModel.builder()
            .modelName("command-a-vision-07-2025")
            .apiKey(System.getenv("CO_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(COHERE_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        CohereChatModel.Builder cohereChatModelBuilder = CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true);

        if (parameters.modelName() == null) {
            cohereChatModelBuilder.modelName("command-r7b-12-2024");
        }
        return cohereChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "command-r-plus-08-2024";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    public List<ChatModel> modelsSupportingStructuredOutputs() {
        return List.of(COHERE_CHAT_MODEL);
    }

    @Override
    public List<ChatModel> modelsSupportingImageInputs() {
        return List.of(COHERE_VISION_MODEL);
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return CohereChatResponseMetadata.class;
    }
}
