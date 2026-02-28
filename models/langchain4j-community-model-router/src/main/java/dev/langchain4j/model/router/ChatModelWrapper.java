package dev.langchain4j.model.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Wraps a {@link ChatModel} or {@link StreamingChatModel} adding optional routing metadata.
 */
@Experimental
public class ChatModelWrapper implements ChatModel, StreamingChatModel {

    private final ChatModel model;
    private final StreamingChatModel streamingModel;
    private Map<String, Serializable> metadata;
    private List<ChatModelListener> listeners = new ArrayList<>();

    ChatModelWrapper(ChatModel model, Map<String, Serializable> metadata) {
        this.model = Objects.requireNonNull(model, "model");
        this.streamingModel = model instanceof StreamingChatModel streamingChatModel ? streamingChatModel : null;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    ChatModelWrapper(StreamingChatModel streamingModel, Map<String, Serializable> routingMetadata) {
        this.streamingModel = Objects.requireNonNull(streamingModel, "streamingModel");
        this.model = streamingModel instanceof ChatModel chatModel ? chatModel : null;
        this.metadata = routingMetadata == null ? new HashMap<>() : new HashMap<>(routingMetadata);
    }

    public ChatModelWrapper(ChatModel model) {
        this(model, new HashMap<>());
    }

    public ChatModelWrapper(StreamingChatModel streamingModel) {
        this(streamingModel, new HashMap<>());
    }

    public ChatModel model() {
        return model;
    }

    public Map<String, Serializable> routingMetadata() {
        return metadata;
    }

    public Serializable getMetadata(String key) {
        Objects.requireNonNull(key, "key");
        return metadata.get(key);
    }

    public void setMetadata(String key, Serializable value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        if (model == null) {
            throw new UnsupportedOperationException("Synchronous calls not supported by the wrapped model");
        }
        return model.chat(chatRequest);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        if (streamingModel == null) {
            throw new UnsupportedOperationException("Streaming not supported by the wrapped model");
        }
        streamingModel.chat(chatRequest, handler);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        if (model != null) {
            return model.supportedCapabilities();
        }
        if (streamingModel != null) {
            return streamingModel.supportedCapabilities();
        }
        throw new NullPointerException("both model and streamingModel are null");
    }

    @Override
    public ModelProvider provider() {
        if (model != null) {
            return model.provider();
        }
        if (streamingModel != null) {
            return streamingModel.provider();
        }
        throw new NullPointerException("both model and streamingModel are null");
    }

    @Override
    public List<ChatModelListener> listeners() {
        if (model != null) {
            return Stream.concat(model.listeners().stream(), listeners.stream()).toList();
        }
        if (streamingModel != null) {
            return Stream.concat(streamingModel.listeners().stream(), listeners.stream())
                    .toList();
        }
        throw new NullPointerException("both model and streamingModel are null");
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        if (model != null) {
            return model.defaultRequestParameters();
        }
        if (streamingModel != null) {
            return streamingModel.defaultRequestParameters();
        }
        throw new NullPointerException("both model and streamingModel are null");
    }

    public boolean isStreaming() {
        return streamingModel != null;
    }

    public boolean addListener(ChatModelListener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(ChatModelListener listener) {
        return listeners.remove(listener);
    }
}
