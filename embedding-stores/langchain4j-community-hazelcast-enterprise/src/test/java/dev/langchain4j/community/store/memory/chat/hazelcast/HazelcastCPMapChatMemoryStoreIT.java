package dev.langchain4j.community.store.memory.chat.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for {@link HazelcastCPMapChatMemoryStore}.
 * <p>
 * A {@link com.hazelcast.cp.CPMap} requires the CP Subsystem, which needs a quorum of at least
 * {@code MIN_GROUP_SIZE} (= 3) members. This test therefore starts a three-member embedded cluster
 * in-process.
 * <p>
 * Requires a Hazelcast Enterprise license key in the {@code HZ_LICENSEKEY} environment variable;
 * the class is skipped (e.g. in CI) when it is not set.
 */
@EnabledIfEnvironmentVariable(named = "HZ_LICENSEKEY", matches = ".+")
class HazelcastCPMapChatMemoryStoreIT {

    private static final int CP_MEMBER_COUNT = 3;
    private static final int BASE_PORT = 5710;

    static final List<HazelcastInstance> members = new ArrayList<>();

    static HazelcastInstance hazelcastInstance;

    private final String userId = "someUserId";

    private HazelcastCPMapChatMemoryStore memoryStore;

    @BeforeAll
    static void startCluster() throws InterruptedException {
        String licenseKey = System.getenv("HZ_LICENSEKEY");

        List<String> memberAddresses =
                List.of("127.0.0.1:" + BASE_PORT, "127.0.0.1:" + (BASE_PORT + 1), "127.0.0.1:" + (BASE_PORT + 2));

        for (int i = 0; i < CP_MEMBER_COUNT; i++) {
            Config config = new Config();
            config.setClusterName("langchain4j-cpmap-it");
            config.setLicenseKey(licenseKey);
            config.getCPSubsystemConfig().setCPMemberCount(CP_MEMBER_COUNT);

            NetworkConfig network = config.getNetworkConfig();
            network.setPort(BASE_PORT).setPortAutoIncrement(true);
            JoinConfig join = network.getJoin();
            join.getMulticastConfig().setEnabled(false);
            join.getTcpIpConfig().setEnabled(true).setMembers(memberAddresses);

            members.add(Hazelcast.newHazelcastInstance(config));
        }

        hazelcastInstance = members.get(0);
        // Block until the CP Subsystem has formed before any test runs.
        hazelcastInstance
                .getCPSubsystem()
                .getCPSubsystemManagementService()
                .awaitUntilDiscoveryCompleted(60, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopCluster() {
        members.forEach(HazelcastInstance::shutdown);
        members.clear();
    }

    @BeforeEach
    void setUp() {
        memoryStore = HazelcastCPMapChatMemoryStore.builder()
                .hazelcastInstance(hazelcastInstance)
                .name("chatMemory")
                .build();
        memoryStore.deleteMessages(userId);
        assertThat(memoryStore.getMessages(userId)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Core CRUD behaviour
    // -------------------------------------------------------------------------

    @Test
    void should_set_and_get_messages() {
        String sysMessage = "You are a large language model working with LangChain4j";

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage(sysMessage));
        chatMessages.add(new UserMessage("Hello"));
        memoryStore.updateMessages(userId, chatMessages);

        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(messages.get(1).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo(sysMessage);
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
    void should_delete_messages() {
        memoryStore.updateMessages(userId, List.of(new SystemMessage("You are a helpful assistant")));
        assertThat(memoryStore.getMessages(userId)).hasSize(1);

        memoryStore.deleteMessages(userId);

        assertThat(memoryStore.getMessages(userId)).isEmpty();
    }

    @Test
    void should_isolate_different_memory_ids() {
        memoryStore.updateMessages("alice", List.of(new UserMessage("Alice's message")));
        memoryStore.updateMessages("bob", List.of(new UserMessage("Bob's message")));

        assertThat(memoryStore.getMessages("alice")).containsExactly(new UserMessage("Alice's message"));
        assertThat(memoryStore.getMessages("bob")).containsExactly(new UserMessage("Bob's message"));

        // CPMap has no clear(); delete the non-default keys so they don't leak into other tests
        memoryStore.deleteMessages("alice");
        memoryStore.deleteMessages("bob");
    }

    @Test
    void should_return_empty_list_for_unknown_memory_id() {
        assertThat(memoryStore.getMessages("unknown-cpmap-id")).isEmpty();
    }

    @Test
    void should_delete_non_existent_memory_id_without_error() {
        memoryStore.deleteMessages("does-not-exist"); // must not throw
    }

    @Test
    void should_support_non_string_memory_id() {
        Integer memoryId = 42;
        memoryStore.updateMessages(memoryId, List.of(new UserMessage("hi")));

        assertThat(memoryStore.getMessages(memoryId)).containsExactly(new UserMessage("hi"));
        assertThat(memoryStore.getMessages("42")).containsExactly(new UserMessage("hi"));

        // CPMap has no clear(); delete the key this test wrote so it doesn't leak into other tests
        memoryStore.deleteMessages(memoryId);
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
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, new ArrayList<>()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }

    // Builder/factory validation that needs no cluster lives in HazelcastCPMapChatMemoryStoreTest
    // so it runs without a license.
}
