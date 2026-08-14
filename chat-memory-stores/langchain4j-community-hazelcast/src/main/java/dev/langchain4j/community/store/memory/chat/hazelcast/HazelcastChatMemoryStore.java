package dev.langchain4j.community.store.memory.chat.hazelcast;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChatMemoryStore} backed by a Hazelcast {@link IMap}.
 * <p>
 * The {@link HazelcastChatMemoryStore} supports memory identifiers of any type
 * whose {@code toString()} produces a valid and stable map key. The key type
 * must properly implement {@code equals()} and {@code hashCode()}.
 * <p>
 * Each chat memory id is stored as a {@link String} key in the {@link IMap};
 * the value is a JSON-serialised list of {@link ChatMessage}s produced by
 * {@link ChatMessageSerializer}.
 * <p>
 * Hazelcast can operate in two distinct topologies, and the way a
 * {@link HazelcastInstance} is obtained differs between them:
 *
 * <h2>Embedded member (Hazelcast runs inside the application JVM)</h2>
 * <pre>{@code
 * HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());
 *
 * ChatMemoryStore store = HazelcastChatMemoryStore.builder()
 *         .hazelcastInstance(hz)
 *         .build();
 * }</pre>
 *
 * <h2>Client/server (Hazelcast runs as a separate cluster)</h2>
 * <pre>{@code
 * ClientConfig clientConfig = new ClientConfig();
 * clientConfig.getNetworkConfig().addAddress("hazelcast-host:5701");
 * // configure TLS, credentials, etc. as required
 * HazelcastInstance hzClient = HazelcastClient.newHazelcastClient(clientConfig);
 *
 * ChatMemoryStore store = HazelcastChatMemoryStore.builder()
 *         .hazelcastInstance(hzClient)
 *         .build();
 * }</pre>
 *
 * <h2>Custom map name</h2>
 * <pre>{@code
 * ChatMemoryStore store = HazelcastChatMemoryStore.builder()
 *         .hazelcastInstance(hz)
 *         .name("my-chat-memory")
 *         .build();
 * }</pre>
 *
 * <h2>Pre-configured IMap</h2>
 * <pre>{@code
 * IMap<String, String> imap = hz.getMap("my-chat-memory");
 * ChatMemoryStore store = HazelcastChatMemoryStore.create(imap);
 * }</pre>
 */
public class HazelcastChatMemoryStore implements ChatMemoryStore {

    /**
     * The default {@link IMap} name.
     */
    public static final String DEFAULT_MAP_NAME = "chatMemory";

    /**
     * The {@link IMap} used to store the chat messages.
     */
    protected final IMap<String, String> chatMemory;

    /**
     * Create a {@link HazelcastChatMemoryStore}.
     * <p>
     * This constructor is protected; instances are created via the static
     * factory method or the builder.
     *
     * @param chatMemory the {@link IMap} to store the chat history
     */
    protected HazelcastChatMemoryStore(IMap<String, String> chatMemory) {
        this.chatMemory = ensureNotNull(chatMemory, "chatMemory");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        validateId(memoryId);
        String json = chatMemory.get(key(memoryId));
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        validateId(memoryId);
        String json = ChatMessageSerializer.messagesToJson(ensureNotEmpty(messages, "messages"));
        chatMemory.set(key(memoryId), json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        validateId(memoryId);
        chatMemory.delete(key(memoryId));
    }

    private void validateId(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
    }

    /**
     * Derive the {@link IMap} key for a memory id. Memory ids of any type are
     * supported; the key is the id's {@code toString()}, which must be valid and
     * stable for the id type in use.
     */
    private static String key(Object memoryId) {
        return String.valueOf(memoryId);
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    /**
     * Create a {@link HazelcastChatMemoryStore} that uses a pre-configured
     * {@link IMap} directly.
     * <p>
     * This is the most explicit construction path and works identically
     * regardless of whether the {@link IMap} was obtained from an embedded
     * member or a {@link com.hazelcast.client.HazelcastClient}.
     *
     * @param map the {@link IMap} used to store chat messages; must not be {@code null}
     * @return a {@link HazelcastChatMemoryStore}
     */
    public static HazelcastChatMemoryStore create(IMap<String, String> map) {
        return new HazelcastChatMemoryStore(map);
    }

    /**
     * Return a {@link Builder} to build a {@link HazelcastChatMemoryStore}.
     *
     * @return a {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * A builder to create {@link HazelcastChatMemoryStore} instances.
     * <p>
     * A {@link HazelcastInstance} <em>must</em> be supplied
     * explicitly via {@link #hazelcastInstance(HazelcastInstance)}.
     * The instance can represent either an embedded cluster member
     * ({@code Hazelcast.newHazelcastInstance(...)}) or a thin client
     * ({@code HazelcastClient.newHazelcastClient(...)}); the builder does not
     * distinguish between the two.
     */
    public static class Builder {

        /**
         * The name of the {@link IMap} to contain the serialised
         * {@link ChatMessage chat messages}.
         */
        private String name = DEFAULT_MAP_NAME;

        /**
         * The {@link HazelcastInstance} used to obtain the
         * {@link IMap}. Must be set explicitly — there is no implicit fallback
         * because the correct instance (embedded member vs. client) depends on
         * the deployment topology and cannot be inferred automatically.
         */
        private HazelcastInstance hazelcastInstance;

        /**
         * Create a {@link Builder}.
         */
        protected Builder() {}

        /**
         * Set the name of the {@link IMap} that will hold the serialised
         * {@link ChatMessage chat messages}.
         * <p>
         * If {@code name} is {@code null} or blank, {@link #DEFAULT_MAP_NAME}
         * is used instead.
         *
         * @param name the map name
         * @return this builder for fluent method calls
         */
        public Builder name(String name) {
            this.name = isNullOrBlank(name) ? DEFAULT_MAP_NAME : name;
            return this;
        }

        /**
         * Set the {@link HazelcastInstance} used to obtain
         * the {@link IMap}.
         * <p>
         * This is a <strong>required</strong> parameter. Pass either:
         * <ul>
         *   <li>an embedded member instance:
         *       {@code Hazelcast.newHazelcastInstance(config)}, or</li>
         *   <li>a client instance connecting to an external cluster:
         *       {@code HazelcastClient.newHazelcastClient(clientConfig)}.</li>
         * </ul>
         *
         * @param hazelcastInstance the Hazelcast instance; must not be {@code null}
         * @return this builder for fluent method calls
         */
        public Builder hazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance;
            return this;
        }

        /**
         * Creates a new {@link HazelcastChatMemoryStore}.
         *
         * @return a new {@link HazelcastChatMemoryStore}
         * @throws IllegalArgumentException if {@code hazelcastInstance} was not set
         */
        public HazelcastChatMemoryStore build() {
            ensureNotNull(
                    hazelcastInstance,
                    "hazelcastInstance is required. Supply an embedded member instance "
                            + "(Hazelcast.newHazelcastInstance) or a client instance "
                            + "(HazelcastClient.newHazelcastClient) depending on your deployment topology.");
            IMap<String, String> map = hazelcastInstance.getMap(name);
            return new HazelcastChatMemoryStore(map);
        }
    }
}
