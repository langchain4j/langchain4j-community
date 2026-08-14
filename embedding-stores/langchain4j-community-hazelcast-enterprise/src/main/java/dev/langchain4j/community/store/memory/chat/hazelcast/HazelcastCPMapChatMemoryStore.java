package dev.langchain4j.community.store.memory.chat.hazelcast;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.CPMap;
import com.hazelcast.cp.CPSubsystem;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChatMemoryStore} backed by a Hazelcast Enterprise {@link CPMap}.
 * <p>
 * Unlike the {@code IMap}-based store, a {@link CPMap} lives in the
 * <a href="https://docs.hazelcast.com/hazelcast/latest/cp-subsystem/cp-subsystem">CP Subsystem</a>
 * and is <strong>linearizable</strong> (strongly consistent, backed by Raft). This avoids lost
 * updates when the same {@code memoryId} is updated concurrently and keeps reads correct during
 * network partitions — the minority side becomes unavailable rather than returning stale data.
 *
 * <p>Each chat memory id is stored as a {@link String} key; the value is a JSON-serialised list of
 * {@link ChatMessage}s produced by {@link ChatMessageSerializer}.
 *
 * <p><strong>Requirements and trade-offs:</strong>
 * <ul>
 *   <li>Hazelcast <strong>Enterprise</strong> Edition with a valid license key.</li>
 *   <li>The CP Subsystem must be enabled, e.g.
 *       {@code config.getCPSubsystemConfig().setCPMemberCount(3)} (a quorum of at least
 *       {@code MIN_GROUP_SIZE} = 3 members). The builder fails fast if it is not enabled.</li>
 *   <li>A {@link CPMap} is <strong>not partitioned</strong>; it must fit within each CP member's
 *       RAM (see {@code CPMapConfig}, default 100 MB total). It suits many small conversations,
 *       not unbounded history for very large user populations — prefer the {@code IMap}-based
 *       store for that.</li>
 *   <li>A {@link CPMap} has no TTL/eviction: conversations live until {@link #deleteMessages(Object)}
 *       is called.</li>
 * </ul>
 *
 * <h2>Embedded-member example</h2>
 * <pre>{@code
 * Config config = new Config();
 * config.getCPSubsystemConfig().setCPMemberCount(3); // CP Subsystem must be enabled
 * HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
 *
 * ChatMemoryStore store = HazelcastCPMapChatMemoryStore.builder()
 *         .hazelcastInstance(hz)
 *         .name("chatMemory")
 *         .build();
 * }</pre>
 */
public class HazelcastCPMapChatMemoryStore implements ChatMemoryStore {

    /**
     * The default {@link CPMap} name.
     */
    public static final String DEFAULT_MAP_NAME = "chatMemory";

    /**
     * The {@link CPMap} used to store the chat messages.
     */
    protected final CPMap<String, String> chatMemory;

    /**
     * Create a {@link HazelcastCPMapChatMemoryStore}.
     * <p>
     * This constructor is protected; instances are created via the static factory method or the
     * builder.
     *
     * @param chatMemory the {@link CPMap} to store the chat history
     */
    protected HazelcastCPMapChatMemoryStore(CPMap<String, String> chatMemory) {
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
     * Derive the {@link CPMap} key for a memory id. Memory ids of any type are supported; the key is
     * the id's {@code toString()}, which must be valid and stable for the id type in use.
     */
    private static String key(Object memoryId) {
        return String.valueOf(memoryId);
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    /**
     * Create a {@link HazelcastCPMapChatMemoryStore} that uses a pre-configured {@link CPMap}
     * directly.
     *
     * @param map the {@link CPMap} used to store chat messages; must not be {@code null}
     * @return a {@link HazelcastCPMapChatMemoryStore}
     */
    public static HazelcastCPMapChatMemoryStore create(CPMap<String, String> map) {
        return new HazelcastCPMapChatMemoryStore(map);
    }

    /**
     * Return a {@link Builder} to build a {@link HazelcastCPMapChatMemoryStore}.
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
     * A builder to create {@link HazelcastCPMapChatMemoryStore} instances.
     */
    public static class Builder {

        private String name = DEFAULT_MAP_NAME;

        private HazelcastInstance hazelcastInstance;

        /**
         * Create a {@link Builder}.
         */
        protected Builder() {}

        /**
         * Set the name of the {@link CPMap} that will hold the serialised chat messages.
         * <p>
         * If {@code name} is {@code null} or blank, {@link #DEFAULT_MAP_NAME} is used instead.
         *
         * @param name the map name
         * @return this builder for fluent method calls
         */
        public Builder name(String name) {
            this.name = isNullOrBlank(name) ? DEFAULT_MAP_NAME : name;
            return this;
        }

        /**
         * Set the {@link HazelcastInstance} used to obtain the {@link CPMap}. The instance must have
         * the CP Subsystem enabled. This is a <strong>required</strong> parameter.
         *
         * @param hazelcastInstance the Hazelcast instance; must not be {@code null}
         * @return this builder for fluent method calls
         */
        public Builder hazelcastInstance(HazelcastInstance hazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance;
            return this;
        }

        /**
         * Creates a new {@link HazelcastCPMapChatMemoryStore}.
         *
         * @return a new {@link HazelcastCPMapChatMemoryStore}
         * @throws IllegalArgumentException if {@code hazelcastInstance} was not set
         * @throws IllegalStateException if the CP Subsystem is not enabled on the instance
         */
        public HazelcastCPMapChatMemoryStore build() {
            ensureNotNull(
                    hazelcastInstance,
                    "hazelcastInstance is required. Supply an Enterprise instance with the CP "
                            + "Subsystem enabled (config.getCPSubsystemConfig().setCPMemberCount(...)).");
            CPSubsystem cpSubsystem = hazelcastInstance.getCPSubsystem();
            CPMap<String, String> map;
            try {
                map = cpSubsystem.getMap(name);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Unable to obtain CPMap '" + name + "'. Ensure this is a Hazelcast Enterprise "
                                + "instance with the CP Subsystem enabled "
                                + "(config.getCPSubsystemConfig().setCPMemberCount(...)).",
                        e);
            }
            return new HazelcastCPMapChatMemoryStore(map);
        }
    }
}
