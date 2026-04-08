package dev.langchain4j.model.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

class FailoverStrategyTest {

    @Test
    void skipsFailedModelsDuringCooldown() {
        ChatModelWrapper primary = new ChatModelWrapper(new NoOpChatModel());
        primary.setMetadata(FailoverStrategy.FAILED, Boolean.TRUE);
        primary.setMetadata(FailoverStrategy.FAILED_TIME, Instant.now());

        ChatModelWrapper secondary = new ChatModelWrapper(new NoOpChatModel());

        List<ChatModelWrapper> routes = List.of(primary, secondary);

        FailoverStrategy strategy = new FailoverStrategy(Duration.ofMinutes(1));

        ChatModelWrapper route = strategy.route(
                routes, ChatRequest.builder().messages(new UserMessage("ping")).build());

        assertEquals(secondary, route);
    }

    @Test
    void returnsModelAfterCooldownExpires() {
        ChatModelWrapper primary = new ChatModelWrapper(new NoOpChatModel());
        primary.setMetadata(FailoverStrategy.FAILED, Boolean.TRUE);
        primary.setMetadata(FailoverStrategy.FAILED_TIME, Instant.now().minus(Duration.ofMinutes(5)));

        ChatModelWrapper secondary = new ChatModelWrapper(new NoOpChatModel());

        List<ChatModelWrapper> routes = List.of(primary, secondary);

        FailoverStrategy strategy = new FailoverStrategy(Duration.ofMinutes(1));

        ChatModelWrapper route = strategy.route(
                routes, ChatRequest.builder().messages(new UserMessage("ping")).build());

        assertEquals(primary, route);
        assertNull(primary.getMetadata(FailoverStrategy.FAILED));
        assertNull(primary.getMetadata(FailoverStrategy.FAILED_TIME));
        assertNull(primary.getMetadata(FailoverStrategy.FAILED_REASON));
    }

    @Test
    void delegatesOnlyHealthyModels() {
        ChatModelWrapper failed = new ChatModelWrapper(new NoOpChatModel());
        failed.setMetadata(FailoverStrategy.FAILED, Boolean.TRUE);
        failed.setMetadata(FailoverStrategy.FAILED_TIME, Instant.now());

        ChatModelWrapper healthy = new ChatModelWrapper(new NoOpChatModel());

        List<ChatModelWrapper> routes = List.of(failed, healthy);

        ModelRoutingStrategy delegate = (available, chatRequest) -> available.get(0);
        FailoverStrategy strategy = new FailoverStrategy(delegate, Duration.ofMinutes(1));

        ChatModelWrapper route = strategy.route(
                routes, ChatRequest.builder().messages(new UserMessage("ping")).build());

        assertEquals(healthy, route);
    }

    @Test
    void returnsNullWhenAllRoutesAreInCooldown() {
        ChatModelWrapper first = new ChatModelWrapper(new NoOpChatModel());
        first.setMetadata(FailoverStrategy.FAILED, Boolean.TRUE);
        first.setMetadata(FailoverStrategy.FAILED_TIME, Instant.now());

        ChatModelWrapper second = new ChatModelWrapper(new NoOpChatModel());
        second.setMetadata(FailoverStrategy.FAILED, Boolean.TRUE);
        second.setMetadata(FailoverStrategy.FAILED_TIME, Instant.now());

        FailoverStrategy strategy = new FailoverStrategy(Duration.ofMinutes(1));

        ChatModelWrapper route = strategy.route(
                List.of(first, second),
                ChatRequest.builder().messages(new UserMessage("ping")).build());

        assertNull(route);
    }

    private static class NoOpChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage("")).build();
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
        @EnabledIfEnvironmentVariable(named = "FIRST_AZURE_OPENAI_ENDPOINT", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "FIRST_AZURE_OPENAI_DEPLOYMENT_NAME", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "SECOND_AZURE_OPENAI_KEY", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "SECOND_AZURE_OPENAI_ENDPOINT", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "SECOND_AZURE_OPENAI_DEPLOYMENT_NAME", matches = ".+")
    })
    void ignoreFailedModelIT() {
        ChatModel firstModel = AzureOpenAiChatModel.builder()
                .apiKey("INVALID")
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
                .routingStrategy(new FailoverStrategy())
                .build();
        assertNotNull(router.chat(new UserMessage("hello")));
    }
}
