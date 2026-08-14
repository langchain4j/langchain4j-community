package dev.langchain4j.community.store.memory.chat.duckdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

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
        assertThat(store.getMessages("test")).isEqualTo(messages);
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
        assertThat(store.getMessages("to_delete")).isEmpty();
        assertThat(store.getMessages("to_keep")).isEqualTo(toKeepMessages);
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void should_clean_expired_messages(DuckDBChatMemoryStore store) throws Exception {
        List<ChatMessage> messages = List.of(new UserMessage("will be clean"));
        store.updateMessages("test", messages);
        Thread.sleep(expirationTest.toMillis() + 50);
        store.cleanExpiredMessage();
        assertThat(store.getMessages("test")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void memory_id_cannot_be_null(DuckDBChatMemoryStore store) {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> store.getMessages(null));
        List<ChatMessage> messages = List.of(new SystemMessage("system message"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> store.updateMessages(null, messages));
    }

    @ParameterizedTest
    @MethodSource("provideStores")
    void messages_cannot_be_null(DuckDBChatMemoryStore store) {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> store.updateMessages("test", null));
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
