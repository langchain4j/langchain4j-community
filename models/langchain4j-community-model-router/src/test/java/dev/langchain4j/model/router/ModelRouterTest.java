package dev.langchain4j.model.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelRouterTest {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(new UserMessage("ping")).build();

    private static class NoOpChatModel implements ChatModel {

        private final String id;

        NoOpChatModel(String id) {
            this.id = id;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage(id)).build();
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

    private static class AlwaysThrowingChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new IllegalStateException("permanent delegate failure");
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

    /**
     * When every route persistently fails and a non-failover strategy is used, the
     * router must surface the underlying failure instead of recursing unboundedly and
     * crashing with a {@link StackOverflowError}.
     */
    @Test
    void propagatesFailureInsteadOfRecursingUnboundedly() {
        ModelRouter router = ModelRouter.builder()
                .addRoutes(new AlwaysThrowingChatModel())
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();

        Exception thrown = assertThrows(IllegalStateException.class, () -> router.chat(REQUEST));
        assertEquals("permanent delegate failure", thrown.getMessage());
    }

    /**
     * With a {@link FailoverStrategy}, a failing route is skipped and the request is
     * routed to the next healthy model within a bounded number of attempts.
     */
    @Test
    void failsOverToHealthyModel() {
        ModelRouter router = ModelRouter.builder()
                .addRoutes(new AlwaysThrowingChatModel(), new NoOpChatModel("healthy"))
                .routingStrategy(new FailoverStrategy())
                .build();

        ChatResponse response = router.chat(REQUEST);
        assertEquals("healthy", response.aiMessage().text());
    }

    /**
     * After the bounded failover attempts are exhausted, the last model's failure
     * surfaces instead of the router recursing forever.
     */
    @Test
    void boundedAttemptsWhenStrategyKeepsSelectingSameFailingModel() {
        // A throttling/usage strategy that does not mark models failed will keep
        // selecting the (single) failing route; the router must still terminate.
        AtomicInteger attempts = new AtomicInteger();
        ChatModel flaky = new ChatModel() {

            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                attempts.incrementAndGet();
                throw new IllegalStateException("flaky");
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
        };
        ModelRouter router = ModelRouter.builder()
                .addRoutes(flaky)
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> router.chat(REQUEST));
        assertEquals("flaky", thrown.getMessage());
        // With a single route, the router attempts it once before propagating.
        assertEquals(1, attempts.get());
    }

    /**
     * A successful synchronous call is routed and returned unchanged.
     */
    @Test
    void successfulCallDelegatesToRoute() {
        ModelRouter router = ModelRouter.builder()
                .addRoutes(new NoOpChatModel("ok"))
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();

        ChatResponse response = router.chat(REQUEST);
        assertEquals("ok", response.aiMessage().text());
    }
}
