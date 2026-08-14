package dev.langchain4j.community.store.memory.chat.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for {@link HazelcastChatMemoryStore}.
 * <p>
 * All tests run against a single embedded Hazelcast member. Testing with an
 * embedded member is sufficient because the store only calls {@link com.hazelcast.map.IMap}
 * methods — the topology behind the map (embedded vs. remote cluster) is
 * transparent to the store and does not affect its behaviour.
 */
class HazelcastChatMemoryStoreIT {

    static HazelcastInstance hazelcastInstance;

    private final String userId = "someUserId";

    private HazelcastChatMemoryStore memoryStore;

    @BeforeAll
    static void startHazelcast() {
        Config config = new Config();
        config.setClusterName("langchain4j-test");
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void stopHazelcast() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Use the test display name as the map name so every test gets an isolated map
        this.memoryStore = HazelcastChatMemoryStore.builder()
                .hazelcastInstance(hazelcastInstance)
                .name(testInfo.getDisplayName())
                .build();
        memoryStore.deleteMessages(userId);
        assertThat(memoryStore.getMessages(userId)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Core CRUD behaviour
    // -------------------------------------------------------------------------

    @Test
    void should_set_messages_into_hazelcast() {
        // given
        assertThat(memoryStore.getMessages(userId)).isEmpty();

        // when
        String sysMessage = "You are a large language model working with LangChain4j";
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new ImageContent("someCatImageUrl"));

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage(sysMessage));
        chatMessages.add(new UserMessage("user1", userMsgContents));
        memoryStore.updateMessages(userId, chatMessages);

        // then
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(2);

        assertThat(messages.get(0).type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(messages.get(1).type()).isEqualTo(ChatMessageType.USER);

        SystemMessage sys = (SystemMessage) messages.get(0);
        assertThat(sys.text()).isEqualTo(sysMessage);

        UserMessage usr = (UserMessage) messages.get(1);
        assertThat(usr.contents()).isEqualTo(userMsgContents);
    }

    @Test
    void should_delete_messages_from_hazelcast() {
        // given
        memoryStore.updateMessages(
                userId, List.of(new SystemMessage("You are a large language model working with LangChain4j")));
        assertThat(memoryStore.getMessages(userId)).hasSize(1);

        // when
        memoryStore.deleteMessages(userId);

        // then
        assertThat(memoryStore.getMessages(userId)).isEmpty();
    }

    @Test
    void should_overwrite_messages_on_update() {
        memoryStore.updateMessages(userId, List.of(new SystemMessage("First system prompt")));
        memoryStore.updateMessages(
                userId,
                List.of(new SystemMessage("Updated system prompt"), new UserMessage("Hello"), new AiMessage("Hi!")));

        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(3);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo("Updated system prompt");
    }

    @Test
    void should_isolate_different_memory_ids() {
        memoryStore.updateMessages("alice", List.of(new UserMessage("Alice's message")));
        memoryStore.updateMessages("bob", List.of(new UserMessage("Bob's message")));

        assertThat(memoryStore.getMessages("alice")).containsExactly(new UserMessage("Alice's message"));
        assertThat(memoryStore.getMessages("bob")).containsExactly(new UserMessage("Bob's message"));
    }

    @Test
    void should_return_empty_list_for_unknown_memory_id() {
        assertThat(memoryStore.getMessages("unknown-id")).isEmpty();
    }

    @Test
    void should_support_non_string_memory_id() {
        // given a non-String memory id, the store keys on its toString()
        Integer memoryId = 42;

        // when
        memoryStore.updateMessages(memoryId, List.of(new UserMessage("hi")));

        // then it is retrievable by the original id
        assertThat(memoryStore.getMessages(memoryId)).containsExactly(new UserMessage("hi"));
        // and by the String form of that id, documenting the String.valueOf(memoryId) coercion
        assertThat(memoryStore.getMessages("42")).containsExactly(new UserMessage("hi"));
    }

    @Test
    void should_delete_non_existent_memory_id_without_error() {
        memoryStore.deleteMessages("does-not-exist"); // must not throw
    }

    // -------------------------------------------------------------------------
    // Null / empty validation
    // -------------------------------------------------------------------------

    @Test
    void getMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.getMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }

    @Test
    void updateMessages_messages_null() {
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.updateMessages(
                        null, List.of(new SystemMessage("You are a large language model working with LangChain4j"))))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }

    // -------------------------------------------------------------------------
    // Builder and map-name tests
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource // null and ""
    @ValueSource(strings = {"  ", "  \t\n "}) // blank strings
    void should_use_default_map_name_when_name_is_blank(String name) {
        HazelcastChatMemoryStore store = HazelcastChatMemoryStore.builder()
                .hazelcastInstance(hazelcastInstance)
                .name(name)
                .build();
        assertThat(store.chatMemory.getName()).isEqualTo(HazelcastChatMemoryStore.DEFAULT_MAP_NAME);
    }

    @Test
    void should_use_custom_name_when_set() {
        String name = "custom-chat-memory";
        HazelcastChatMemoryStore store = HazelcastChatMemoryStore.builder()
                .hazelcastInstance(hazelcastInstance)
                .name(name)
                .build();
        assertThat(store.chatMemory.getName()).isEqualTo(name);
    }

    @Test
    void should_accept_pre_configured_imap() {
        var imap = hazelcastInstance.<String, String>getMap("pre-configured-imap");
        HazelcastChatMemoryStore store = HazelcastChatMemoryStore.create(imap);

        store.updateMessages("s1", List.of(new UserMessage("direct imap")));
        assertThat(store.getMessages("s1")).containsExactly(new UserMessage("direct imap"));

        imap.clear();
    }

    @Test
    void should_throw_when_hazelcast_instance_not_supplied_to_builder() {
        assertThatThrownBy(() -> HazelcastChatMemoryStore.builder().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hazelcastInstance is required");
    }

    @Test
    void should_throw_when_null_imap_supplied_to_create() {
        assertThatThrownBy(() -> HazelcastChatMemoryStore.create(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatMemory");
    }
}
