package dev.langchain4j.community.store.memory.chat.cockroachdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CockroachDbChatMemoryStoreIT extends CockroachDbTestBase {

    static CockroachDbChatMemoryStore memory;

    @BeforeAll
    static void initMemory() {
        memory = CockroachDbChatMemoryStore.builder()
                .engine(engine)
                .tableName("chat_memory_it")
                .build();
    }

    @Test
    void roundtrip_messages_for_a_session() {
        String sessionId = UUID.randomUUID().toString();
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("You are helpful."),
                UserMessage.from("Hello"),
                AiMessage.from("Hi there!"));

        memory.updateMessages(sessionId, messages);

        List<ChatMessage> loaded = memory.getMessages(sessionId);
        assertThat(loaded).hasSize(3);
        assertThat(loaded.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(loaded.get(1)).isInstanceOf(UserMessage.class);
        assertThat(loaded.get(2)).isInstanceOf(AiMessage.class);
    }

    @Test
    void update_replaces_existing_history() {
        String sessionId = UUID.randomUUID().toString();
        memory.updateMessages(sessionId, Arrays.asList(UserMessage.from("a"), AiMessage.from("b")));
        memory.updateMessages(sessionId, Arrays.asList(UserMessage.from("c")));

        List<ChatMessage> loaded = memory.getMessages(sessionId);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    void delete_clears_session() {
        String sessionId = UUID.randomUUID().toString();
        memory.updateMessages(sessionId, Arrays.asList(UserMessage.from("a")));
        memory.deleteMessages(sessionId);
        assertThat(memory.getMessages(sessionId)).isEmpty();
    }

    @Test
    void sessions_are_isolated() {
        String s1 = UUID.randomUUID().toString();
        String s2 = UUID.randomUUID().toString();
        memory.updateMessages(s1, Arrays.asList(UserMessage.from("one")));
        memory.updateMessages(s2, Arrays.asList(UserMessage.from("two")));

        assertThat(memory.getMessages(s1)).hasSize(1);
        assertThat(memory.getMessages(s2)).hasSize(1);
        assertThat(((UserMessage) memory.getMessages(s1).get(0)).singleText()).isEqualTo("one");
    }
}
