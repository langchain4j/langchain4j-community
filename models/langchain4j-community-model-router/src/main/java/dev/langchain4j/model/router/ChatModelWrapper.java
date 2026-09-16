package dev.langchain4j.model.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Wraps a {@link ChatModel} adding optional routing metadata.
 *
 * <p>Streaming models are wrapped by {@link StreamingChatModelWrapper} instead: since
 * {@link ChatModel} and {@link dev.langchain4j.model.chat.StreamingChatModel} define conflicting
 * {@code chat(...)}/{@code doChat(...)} signatures, a single wrapper cannot implement both.
 */
@Experimental
public class ChatModelWrapper extends ModelWrapper implements ChatModel {

    // Per-invocation context, rather than shared state: sync and async calls can overlap.
    static final Object ASYNC_INVOCATION = new Object();

    private final ChatModel model;

    ChatModelWrapper(ChatModel model, Map<String, Serializable> metadata) {
        super(metadata);
        this.model = ensureNotNull(model, "model");
    }

    public ChatModelWrapper(ChatModel model) {
        this(model, new HashMap<>());
    }

    public ChatModel model() {
        return model;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return model.chat(chatRequest);
    }

    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        return model.chatAsync(chatRequest);
    }

    @Override
    public CompletableFuture<ChatResponse> chatAsync(ChatRequest chatRequest, ChatRequestOptions options) {
        Map<Object, Object> attributes = new HashMap<>();
        if (options != null) {
            attributes.putAll(options.listenerAttributes());
        }
        attributes.put(ASYNC_INVOCATION, this);
        return ChatModel.super.chatAsync(
                chatRequest,
                ChatRequestOptions.builder().listenerAttributes(attributes).build());
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return model.supportedCapabilities();
    }

    @Override
    public ModelProvider provider() {
        return model.provider();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return mergedListeners(model.listeners(), ownListeners());
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return model.defaultRequestParameters();
    }
}
