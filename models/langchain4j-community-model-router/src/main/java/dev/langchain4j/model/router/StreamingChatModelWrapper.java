package dev.langchain4j.model.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow.Publisher;

/**
 * Wraps a {@link StreamingChatModel} adding optional routing metadata.
 *
 * <p>Synchronous models are wrapped by {@link ChatModelWrapper} instead: since
 * {@link dev.langchain4j.model.chat.ChatModel} and {@link StreamingChatModel} define conflicting
 * {@code chat(...)}/{@code doChat(...)} signatures, a single wrapper cannot implement both.
 */
@Experimental
public class StreamingChatModelWrapper extends ModelWrapper implements StreamingChatModel {

    private final StreamingChatModel model;

    StreamingChatModelWrapper(StreamingChatModel model, Map<String, Serializable> metadata) {
        super(metadata);
        this.model = ensureNotNull(model, "model");
    }

    public StreamingChatModelWrapper(StreamingChatModel model) {
        this(model, new HashMap<>());
    }

    public StreamingChatModel model() {
        return model;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        model.chat(chatRequest, handler);
    }

    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return model.chat(chatRequest);
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
