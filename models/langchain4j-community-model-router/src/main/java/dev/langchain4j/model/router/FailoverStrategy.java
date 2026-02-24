package dev.langchain4j.model.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A {@link ModelRoutingStrategy} that ignores models which have recently failed. It also supports delegating
 * healthy models to another {@link ModelRoutingStrategy}
 */
@Experimental
public class FailoverStrategy extends DelegatingModelRoutingStrategy {

    public static final String FAILED = "FAILED";
    public static final String FAILED_TIME = "FAILED_TIME";
    public static final String FAILED_REASON = "FAILED_REASON";

    private final Duration cooldown;

    /**
     * FailoverStrategy without delegate and 1 minute cooldown
     */
    public FailoverStrategy() {
        this(null, Duration.ofMinutes(1));
    }

    /**
     * FailoverStrategy with custom cooldown
     *
     * @param cooldown
     *            a duration for cooldown
     */
    public FailoverStrategy(Duration cooldown) {
        this(null, cooldown);
    }

    /**
     * FailoverStrategy with delegate and custom cooldown
     *
     * @param delegate
     *            a delegate ModelRoutingStrategy
     * @param cooldown
     *            a duration for cooldown
     */
    public FailoverStrategy(ModelRoutingStrategy delegate, Duration cooldown) {
        super(delegate);
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    @Override
    public ChatModelWrapper route(List<ChatModelWrapper> availableModels, ChatRequest chatRequest) {
        // add FailureListener to each not already having one
        availableModels.stream()
                .filter(m -> m.listeners().stream().noneMatch(FailureListener.class::isInstance))
                .forEach(m -> m.addListener(new FailureListener(m)));

        Instant now = Instant.now();

        List<ChatModelWrapper> healthyModels = availableModels.stream()
                .filter(entry -> !isInCooldown(entry, now))
                .collect(Collectors.toList());
        // try to delegate
        ChatModelWrapper target = delegateRoute(healthyModels, chatRequest);
        // it not, use first healthy model
        if (target == null && !healthyModels.isEmpty()) {
            target = healthyModels.iterator().next();
        }
        return target;
    }

    private boolean isInCooldown(ChatModelWrapper model, Instant now) {
        Object failedValue = model.getMetadata(FAILED);
        if (!(failedValue instanceof Boolean) || !((Boolean) failedValue)) {
            return false;
        }

        Object failedTime = model.getMetadata(FAILED_TIME);
        if (!(failedTime instanceof Instant)) {
            return true;
        }

        boolean inCooldown = ((Instant) failedTime).plus(cooldown).isAfter(now);
        if (!inCooldown) {
            model.setMetadata(FAILED, null);
            model.setMetadata(FAILED_TIME, null);
            model.setMetadata(FAILED_REASON, null);
        }
        return inCooldown;
    }

    /**
     * A ChatModelListener which set error metadata on the wrapper
     */
    class FailureListener implements ChatModelListener {

        private ChatModelWrapper wrapper;
        // we need to remember the wrapper as the listener does not have access to the model
        public FailureListener(ChatModelWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
            wrapper.setMetadata(FAILED, Boolean.TRUE);
            wrapper.setMetadata(FAILED_TIME, Instant.now());
            wrapper.setMetadata(FAILED_REASON, errorContext.error());
        }
    }
}
