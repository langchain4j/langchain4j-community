package dev.langchain4j.model.router;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Wraps a {@link ChatModel} or {@link StreamingChatModel} adding optional routing metadata.
 */
@Experimental
@Internal
class ChatModelWrapper implements ChatModel, StreamingChatModel {
    
    private final ChatModel           model;
    private final StreamingChatModel  streamingModel;
    private Map<String, Serializable> metadata;

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
        }
        else {
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
        Set<Capability> capabilities = new HashSet<>();
        if (model != null) {
            capabilities.addAll(model.supportedCapabilities());
        }
        if (streamingModel != null) {
            capabilities.addAll(streamingModel.supportedCapabilities());
        }
        return capabilities;
    }
    
    @Override
    public ModelProvider provider() {
        if (model != null) {
            return model.provider();
        }
        if (streamingModel != null) {
            return streamingModel.provider();
        }
        return ChatModel.super.provider();
    }
    
    @Override
    public List<ChatModelListener> listeners() {
        if (model != null) {
            return model.listeners();
        }
        if (streamingModel != null) {
            return streamingModel.listeners();
        }
        return ChatModel.super.listeners();
    }
    
    @Override
    public ChatRequestParameters defaultRequestParameters() {
        if (model != null) {
            return model.defaultRequestParameters();
        }
        if (streamingModel != null) {
            return streamingModel.defaultRequestParameters();
        }
        return ChatModel.super.defaultRequestParameters();
    }
    
    boolean isStreaming() {
    	return streamingModel != null;
    }
}
