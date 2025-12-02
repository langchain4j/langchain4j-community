package dev.langchain4j.community.store.memory.chat.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DuckDBChatMemoryStoreIT {

    @TempDir
    static java.nio.file.Path tempDir;

    static Duration expirationTest = Duration.ofMillis(10);

    @ParameterizedTest
    @MethodSource("provideStores")
    void should_store_and_retrieve_messages(DuckDBChatMemoryStore store) {
        var messages = List.of(
                new SystemMessage("system message"), new UserMessage("user message"), new AiMessage("ai message"));
        store.updateMessages("test", messages);
        assertEquals(messages, store.getMessages("test"));
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void should_delete_messages() {
        var store = DuckDBChatMemoryStore.inMemory();
        List<ChatMessage> toDeleteMessages = List.of(new UserMessage("to delete message"));
        List<ChatMessage> toKeepMessages = List.of(new UserMessage("to keep message"));
        store.updateMessages("to_delete", toDeleteMessages);
        store.updateMessages("to_keep", toKeepMessages);
        store.deleteMessages("to_delete");
        assertTrue(store.getMessages("to_delete").isEmpty());
        assertEquals(toKeepMessages, store.getMessages("to_keep"));
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void should_clean_expired_messages(DuckDBChatMemoryStore store) throws InterruptedException {
        List<ChatMessage> messages = List.of(new UserMessage("will be clean"));
        store.updateMessages("test", messages);
        Thread.sleep(expirationTest.toMillis() + 50);
        store.cleanExpiredMessage();
        assertTrue(store.getMessages("test").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void memory_id_cannot_be_null(DuckDBChatMemoryStore store) {
        assertThrows(IllegalArgumentException.class, () -> store.getMessages(null));
        List<ChatMessage> messages = List.of(new SystemMessage("system message"));
        assertThrows(IllegalArgumentException.class, () -> store.updateMessages(null, messages));
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void messages_cannot_be_null(DuckDBChatMemoryStore store) {
        assertThrows(IllegalArgumentException.class, () -> store.updateMessages("test", null));
    }

    /**
     * Provides a list of DuckDBChatMemoryStore instances for testing
     * both in-memory and file-based store.
     */
    static List<DuckDBChatMemoryStore> provideStores() {
        var tmpFile = tempDir.resolve(UUID.randomUUID() + "-test.db");
        return List.of(
                DuckDBChatMemoryStore.builder()
                        .inMemory("custom_table")
                        .expirationDuration(expirationTest)
                        .build(),
                DuckDBChatMemoryStore.builder()
                        .filePath(tmpFile.toString())
                        .expirationDuration(expirationTest)
                        .build());
    }
}
