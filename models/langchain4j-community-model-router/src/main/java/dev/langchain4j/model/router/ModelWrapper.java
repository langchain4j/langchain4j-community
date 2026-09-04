package dev.langchain4j.model.router;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class for wrappers that add optional routing metadata and additional
 * {@link ChatModelListener}s to a wrapped model.
 *
 * <p>Concrete subclasses wrap either a {@link dev.langchain4j.model.chat.ChatModel}
 * ({@link ChatModelWrapper}) or a {@link dev.langchain4j.model.chat.StreamingChatModel}
 * ({@link StreamingChatModelWrapper}). Routing strategies interact with wrappers only
 * through this class, so the same strategy can be used for both synchronous and
 * streaming routers.
 */
@Experimental
public abstract class ModelWrapper {

    private final Map<String, Serializable> metadata;
    private final List<ChatModelListener> ownListeners = new ArrayList<>();

    protected ModelWrapper(Map<String, Serializable> metadata) {
        // the metadata map must stay mutable: routing strategies store state (e.g. failure
        // markers) on the wrapper via setMetadata
        this.metadata = new HashMap<>(getOrDefault(metadata, Map.of()));
    }

    public Map<String, Serializable> routingMetadata() {
        return metadata;
    }

    public Serializable getMetadata(String key) {
        ensureNotNull(key, "key");
        return metadata.get(key);
    }

    public void setMetadata(String key, Serializable value) {
        ensureNotNull(key, "key");
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    public boolean addListener(ChatModelListener listener) {
        return ownListeners.add(listener);
    }

    public boolean removeListener(ChatModelListener listener) {
        return ownListeners.remove(listener);
    }

    /**
     * The effective listeners of the wrapped model: the listeners of the wrapped model itself
     * merged with the listeners added directly to this wrapper.
     */
    public abstract List<ChatModelListener> listeners();

    /**
     * The listeners that were added directly to this wrapper.
     */
    protected List<ChatModelListener> ownListeners() {
        return ownListeners;
    }

    /**
     * Merges the listeners of the wrapped model with the listeners added directly to this wrapper.
     */
    protected static List<ChatModelListener> mergedListeners(
            List<ChatModelListener> delegateListeners, List<ChatModelListener> ownListeners) {
        return Stream.concat(delegateListeners.stream(), ownListeners.stream()).toList();
    }
}
