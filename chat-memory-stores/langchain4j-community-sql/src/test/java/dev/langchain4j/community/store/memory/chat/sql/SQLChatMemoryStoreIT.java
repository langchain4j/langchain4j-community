package dev.langchain4j.community.store.memory.chat.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class SQLChatMemoryStoreIT {

    protected SQLChatMemoryStore store;

    abstract DataSource dataSource();

    abstract SQLDialect dialect();

    @BeforeEach
    void setUp() {
        store = SQLChatMemoryStore.builder()
                .dataSource(dataSource())
                .sqlDialect(dialect())
                .tableName("chat_memory_test")
                .autoCreateTable(true)
                .build();
    }

    @Test
    void should_return_empty_list_for_unknown_memory_id() {
        assertThat(store.getMessages("unknown-id")).isEmpty();
    }

    @Test
    void should_store_and_retrieve_messages() {
        String memoryId = "user-1";

        List<ChatMessage> messages = List.of(UserMessage.from("Hello"), AiMessage.from("Hi there!"));

        store.updateMessages(memoryId, messages);

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_store_all_message_types() {
        String memoryId = "user-types";

        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("What is 2+2?"),
                AiMessage.from("It is 4."));

        store.updateMessages(memoryId, messages);

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(2)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_update_existing_messages() {
        String memoryId = "user-update";

        List<ChatMessage> initial = new ArrayList<>();
        initial.add(UserMessage.from("Hello"));
        store.updateMessages(memoryId, initial);
        assertThat(store.getMessages(memoryId)).hasSize(1);

        initial.add(AiMessage.from("Hi!"));
        store.updateMessages(memoryId, initial);

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_replace_messages_on_update() {
        String memoryId = "user-replace";

        store.updateMessages(memoryId, List.of(UserMessage.from("Old message 1"), AiMessage.from("Old reply 1")));

        store.updateMessages(memoryId, List.of(UserMessage.from("Brand new message")));

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).isEqualTo("Brand new message");
    }

    @Test
    void should_delete_messages() {
        String memoryId = "user-delete";

        store.updateMessages(memoryId, List.of(UserMessage.from("Delete me")));
        assertThat(store.getMessages(memoryId)).hasSize(1);

        store.deleteMessages(memoryId);

        assertThat(store.getMessages(memoryId)).isEmpty();
    }

    @Test
    void should_delete_messages_for_non_existent_id_without_error() {
        // Should not throw
        store.deleteMessages("does-not-exist");
    }

    @Test
    void should_isolate_messages_by_memory_id() {
        String id1 = "isolation-user-1";
        String id2 = "isolation-user-2";

        store.updateMessages(id1, List.of(UserMessage.from("Hello from user 1")));
        store.updateMessages(id2, List.of(UserMessage.from("Hello from user 2")));

        assertThat(store.getMessages(id1)).containsExactly(UserMessage.from("Hello from user 1"));
        assertThat(store.getMessages(id2)).containsExactly(UserMessage.from("Hello from user 2"));
    }

    @Test
    void deleting_one_memory_id_should_not_affect_others() {
        String id1 = "keep-me";
        String id2 = "delete-me";

        store.updateMessages(id1, List.of(UserMessage.from("Keep")));
        store.updateMessages(id2, List.of(UserMessage.from("Remove")));

        store.deleteMessages(id2);

        assertThat(store.getMessages(id1)).hasSize(1);
        assertThat(store.getMessages(id2)).isEmpty();
    }

    @Test
    void should_handle_large_message_content() {
        String memoryId = "user-large";
        String largeText = "A".repeat(10_000);

        store.updateMessages(memoryId, List.of(UserMessage.from(largeText)));

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(retrieved).hasSize(1);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).isEqualTo(largeText);
    }

    @Test
    void should_handle_special_characters_in_messages() {
        String memoryId = "user-special";
        String specialText = "Hello! <script>alert('xss')</script> & \"quotes\" 'single' \n newlines \t tabs";

        store.updateMessages(memoryId, List.of(UserMessage.from(specialText)));

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).isEqualTo(specialText);
    }

    @Test
    void should_handle_unicode_content() {
        String memoryId = "user-unicode";
        String unicode = "こんにちは 🌸 Привет مرحبا";

        store.updateMessages(memoryId, List.of(UserMessage.from(unicode)));

        List<ChatMessage> retrieved = store.getMessages(memoryId);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).isEqualTo(unicode);
    }

    @Test
    void should_use_custom_table_and_column_names() {
        SQLChatMemoryStore customStore = SQLChatMemoryStore.builder()
                .dataSource(dataSource())
                .sqlDialect(dialect())
                .tableName("custom_memory")
                .memoryIdColumnName("session_id")
                .contentColumnName("messages_json")
                .autoCreateTable(true)
                .build();

        String memoryId = "custom-col-user";
        customStore.updateMessages(memoryId, List.of(UserMessage.from("Custom columns work")));

        assertThat(customStore.getMessages(memoryId)).hasSize(1);
    }

    @Test
    void should_throw_when_auto_create_false_and_table_missing() {
        assertThatThrownBy(() -> SQLChatMemoryStore.builder()
                        .dataSource(dataSource())
                        .sqlDialect(dialect())
                        .tableName("table_that_does_not_exist")
                        .autoCreateTable(false)
                        .build())
                .isInstanceOf(RuntimeException.class);
    }
}
