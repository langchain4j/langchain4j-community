package dev.langchain4j.model.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

class LowestTokenUsageRoutingStrategyTest {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(new UserMessage("ping")).build();

    @Test
    void selectsRouteWithLowestRecordedUsage() {
        ChatModelWrapper highUsage = new ChatModelWrapper(new NoOpChatModel());
        highUsage.setMetadata(LowestTokenUsageRoutingStrategy.TOTAL_TOKEN_USAGE, 100);

        ChatModelWrapper lowUsage = new ChatModelWrapper(new NoOpChatModel());
        lowUsage.setMetadata(LowestTokenUsageRoutingStrategy.TOTAL_TOKEN_USAGE, 5);

        LowestTokenUsageRoutingStrategy strategy = new LowestTokenUsageRoutingStrategy();

        ChatModelWrapper selected = strategy.route(List.of(highUsage, lowUsage), REQUEST);

        assertEquals(lowUsage, selected);
    }

    @Test
    void treatsMissingUsageAsZeroAndPicksFirstOnTie() {
        ChatModelWrapper first = new ChatModelWrapper(new NoOpChatModel());
        ChatModelWrapper second = new ChatModelWrapper(new NoOpChatModel());

        LowestTokenUsageRoutingStrategy strategy = new LowestTokenUsageRoutingStrategy();

        ChatModelWrapper selected = strategy.route(List.of(first, second), REQUEST);

        assertEquals(first, selected);
    }

    @Test
    void returnsNullWhenNoRoutesAreAvailable() {
        LowestTokenUsageRoutingStrategy strategy = new LowestTokenUsageRoutingStrategy();

        ChatModelWrapper selected = strategy.route(List.of(), REQUEST);

        assertNull(selected);
    }

    private static class NoOpChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.EMPTY;
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OTHER;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return List.of();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of();
        }
    }

    @Test
    @EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "FIRST_AZURE_OPENAI_KEY", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "FIRST_AZURE_OPENAI_ENDPOINT", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "FIRST_AZURE_OPENAI_DEPLOYMENT_NAME", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "SECOND_AZURE_OPENAI_KEY", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "SECOND_AZURE_OPENAI_ENDPOINT", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "SECOND_AZURE_OPENAI_DEPLOYMENT_NAME", matches = ".+")
    })
    void lowestTokenUsageRoutingStrategyIT() {
        ChatModel firstModel = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("FIRST_AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("FIRST_AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("FIRST_AZURE_OPENAI_DEPLOYMENT_NAME"))
                .build();

        ChatModel secondModel = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("SECOND_AZURE_OPENAI_KEY"))
                .endpoint(System.getenv("SECOND_AZURE_OPENAI_ENDPOINT"))
                .deploymentName(System.getenv("SECOND_AZURE_OPENAI_DEPLOYMENT_NAME"))
                .build();

        ModelRouter router = ModelRouter.builder()
                .addRoutes(firstModel, secondModel)
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();
        ChatResponse first = router.chat(new UserMessage("hello"));
        ChatResponse second = router.chat(new UserMessage("hello"));
        assertNotEquals(first.modelName(), second.modelName(), "failed to pick unused model for second call");
        ChatResponse third = router.chat(new UserMessage("hello"));
        assertEquals(first.modelName(), third.modelName(), "failed to pick first model for third call");
    }
}
