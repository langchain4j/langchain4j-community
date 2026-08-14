package dev.langchain4j.community.store.memory.chat.hazelcast;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HazelcastCPMapChatMemoryStore} that need neither a running cluster nor a
 * Hazelcast Enterprise license, so they always run (unlike {@link HazelcastCPMapChatMemoryStoreIT},
 * which is skipped without a license).
 */
class HazelcastCPMapChatMemoryStoreTest {

    @Test
    void builder_throws_when_hazelcast_instance_not_supplied() {
        assertThatThrownBy(() -> HazelcastCPMapChatMemoryStore.builder().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hazelcastInstance is required");
    }

    @Test
    void create_throws_when_null_cpmap_supplied() {
        assertThatThrownBy(() -> HazelcastCPMapChatMemoryStore.create(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatMemory");
    }
}
