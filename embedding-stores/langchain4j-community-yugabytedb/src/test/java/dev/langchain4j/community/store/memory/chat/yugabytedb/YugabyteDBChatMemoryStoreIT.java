package dev.langchain4j.community.store.memory.chat.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBEngine;
import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Basic functionality integration tests for YugabyteDBChatMemoryStore using TestContainers.
 * Tests core operations: store, retrieve, update, and delete messages.
 *
 * Main class tests use PostgreSQL JDBC Driver.
 * Nested SmartDriverChatMemoryIT class tests use YugabyteDB Smart Driver.
 */
class YugabyteDBChatMemoryStoreIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBChatMemoryStoreIT.class);

    private static YugabyteDBEngine engine;
    private static ChatMemoryStore memoryStore;

    @BeforeAll
    static void setUp() {
        logger.info("🚀 [POSTGRESQL] Initializing Chat Memory Store with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        String host = yugabyteContainer.getHost();
        Integer port = yugabyteContainer.getMappedPort(5433);

        logger.info("📋 [POSTGRESQL] Container details:");
        logger.info("[POSTGRESQL]   - Host: {}", host);
        logger.info("[POSTGRESQL]   - Mapped port: {}", port);
        logger.info("[POSTGRESQL]   - Container ID: {}", yugabyteContainer.getContainerId());

        // Create YugabyteDBEngine for testing
        logger.info("🔧 [POSTGRESQL] Creating YugabyteDBEngine with PostgreSQL driver...");
        engine = YugabyteDBEngine.builder()
                .host(host)
                .port(port)
                .database(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .usePostgreSQLDriver(true) // Explicitly use PostgreSQL driver
                .maxPoolSize(10)
                .build();
        logger.info("✅ [POSTGRESQL] YugabyteDBEngine created successfully");

        // Create memory store
        logger.info("🧠 [POSTGRESQL] Creating chat memory store...");
        memoryStore = YugabyteDBChatMemoryStore.builder()
                .engine(engine)
                .tableName("chat_memory_postgresql")
                .createTableIfNotExists(true)
                .build();

        logger.info("✅ [POSTGRESQL] Chat memory store created successfully");
        logger.info("🎉 [POSTGRESQL] Chat Memory Store setup completed");
    }

    @AfterAll
    static void tearDown() {
        logger.info("🧹 [POSTGRESQL] Starting Chat Memory Store cleanup...");

        if (engine != null) {
            logger.info("[POSTGRESQL] Closing YugabyteDBEngine...");
            engine.close();
            logger.info("[POSTGRESQL] YugabyteDBEngine closed successfully");
        }

        logger.info("✅ [POSTGRESQL] Chat Memory Store cleanup completed");
    }

    // ========================================
    // MODULAR HELPER METHODS
    // ========================================

    private static void testStoreAndRetrieveConversation(
            ChatMemoryStore store, Logger log, String logPrefix, String memoryIdSuffix) {
        log.info("🧪 {} Testing conversation store and retrieve...", logPrefix);

        // Given
        String memoryId = "conversation-test-" + memoryIdSuffix;
        List<ChatMessage> conversation = Arrays.asList(
                SystemMessage.from("You are a helpful AI assistant."),
                UserMessage.from("What is machine learning?"),
                AiMessage.from("Machine learning is a subset of artificial intelligence."),
                UserMessage.from("Can you give me an example?"),
                AiMessage.from("Sure! Email spam detection is a common example."));

        long startTime = System.currentTimeMillis();

        // When - Store conversation
        log.info(
                "💾 {} Storing conversation with {} messages for memoryId: {}",
                logPrefix,
                conversation.size(),
                memoryId);
        store.updateMessages(memoryId, conversation);
        long storeTime = System.currentTimeMillis() - startTime;
        log.info("✅ {} Conversation stored in {}ms", logPrefix, storeTime);

        // Then - Retrieve and verify
        startTime = System.currentTimeMillis();
        List<ChatMessage> retrievedConversation = store.getMessages(memoryId);
        long retrieveTime = System.currentTimeMillis() - startTime;

        log.info(
                "📥 {} Retrieved conversation with {} messages in {}ms",
                logPrefix,
                retrievedConversation.size(),
                retrieveTime);
        assertThat(retrievedConversation).hasSize(5);

        // Verify message types and content
        assertThat(retrievedConversation.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(retrievedConversation.get(1)).isInstanceOf(UserMessage.class);
        assertThat(retrievedConversation.get(2)).isInstanceOf(AiMessage.class);
        assertThat(retrievedConversation.get(3)).isInstanceOf(UserMessage.class);
        assertThat(retrievedConversation.get(4)).isInstanceOf(AiMessage.class);

        log.info("✅ {} Conversation test completed successfully", logPrefix);
    }

    private static void testUpdateExistingConversation(
            ChatMemoryStore store, Logger log, String logPrefix, String memoryIdSuffix) {
        log.info("🧪 {} Testing conversation update...", logPrefix);

        // Given
        String memoryId = "update-test-" + memoryIdSuffix;
        List<ChatMessage> initialMessages = Arrays.asList(UserMessage.from("Hello"));

        List<ChatMessage> updatedMessages = Arrays.asList(
                UserMessage.from("Hello"),
                AiMessage.from("Hi there! How can I help you?"),
                UserMessage.from("What's the weather like?"));

        // When - Store initial messages
        log.info("💾 {} Storing initial {} message(s)", logPrefix, initialMessages.size());
        store.updateMessages(memoryId, initialMessages);

        // Verify initial state
        List<ChatMessage> retrieved1 = store.getMessages(memoryId);
        log.info("📥 {} Initial retrieval: {} message(s)", logPrefix, retrieved1.size());
        assertThat(retrieved1).hasSize(1);

        // Update with more messages
        log.info("🔄 {} Updating with {} message(s)", logPrefix, updatedMessages.size());
        long startTime = System.currentTimeMillis();
        store.updateMessages(memoryId, updatedMessages);
        long updateTime = System.currentTimeMillis() - startTime;
        log.info("✅ {} Messages updated in {}ms", logPrefix, updateTime);

        // Then - Verify updated state
        List<ChatMessage> retrieved2 = store.getMessages(memoryId);
        log.info("📥 {} Updated retrieval: {} message(s)", logPrefix, retrieved2.size());
        assertThat(retrieved2).hasSize(3);

        log.info("✅ {} Conversation update test completed successfully", logPrefix);
    }

    private static void testDeleteMessages(ChatMemoryStore store, Logger log, String logPrefix, String memoryIdSuffix) {
        log.info("🧪 {} Testing message deletion...", logPrefix);

        // Given
        String memoryId = "delete-test-" + memoryIdSuffix;
        List<ChatMessage> messages = Arrays.asList(
                UserMessage.from("This message will be deleted"), AiMessage.from("This response will also be deleted"));

        // When - Store and then delete
        log.info("💾 {} Storing messages for deletion test", logPrefix);
        store.updateMessages(memoryId, messages);

        // Verify messages exist
        List<ChatMessage> beforeDelete = store.getMessages(memoryId);
        log.info("📥 {} Before deletion: {} message(s)", logPrefix, beforeDelete.size());
        assertThat(beforeDelete).hasSize(2);

        // Delete messages
        log.info("🗑️ {} Deleting messages for memoryId: {}", logPrefix, memoryId);
        long startTime = System.currentTimeMillis();
        store.deleteMessages(memoryId);
        long deleteTime = System.currentTimeMillis() - startTime;
        log.info("✅ {} Messages deleted in {}ms", logPrefix, deleteTime);

        // Then - Verify deletion
        List<ChatMessage> afterDelete = store.getMessages(memoryId);
        log.info("📥 {} After deletion: {} message(s)", logPrefix, afterDelete.size());
        assertThat(afterDelete).isEmpty();

        log.info("✅ {} Message deletion test completed successfully", logPrefix);
    }

    private static void testNonExistentMemoryId(ChatMemoryStore store, Logger log, String logPrefix) {
        log.info("🧪 {} Testing non-existent memory ID handling...", logPrefix);

        // Given
        String nonExistentId = "non-existent-" + UUID.randomUUID();

        // When - Try to retrieve non-existent messages
        log.info("📥 {} Attempting to retrieve messages for non-existent ID: {}", logPrefix, nonExistentId);
        long startTime = System.currentTimeMillis();
        List<ChatMessage> messages = store.getMessages(nonExistentId);
        long retrieveTime = System.currentTimeMillis() - startTime;

        // Then - Should return empty list
        log.info("📥 {} Retrieved {} message(s) in {}ms for non-existent ID", logPrefix, messages.size(), retrieveTime);
        assertThat(messages).isEmpty();

        log.info("✅ {} Non-existent memory ID test completed successfully", logPrefix);
    }

    private static void testNullAndEmptyInputs(ChatMemoryStore store, Logger log, String logPrefix) {
        log.info("🧪 {} Testing null and empty input handling...", logPrefix);

        // Test null memory ID
        log.info("🔍 {} Testing null memory ID...", logPrefix);
        assertThatThrownBy(() -> store.getMessages(null)).isInstanceOf(IllegalArgumentException.class);

        // Test empty memory ID
        log.info("🔍 {} Testing empty memory ID...", logPrefix);
        assertThatThrownBy(() -> store.getMessages("")).isInstanceOf(IllegalArgumentException.class);

        // Test null messages list
        log.info("🔍 {} Testing null messages list...", logPrefix);
        assertThatThrownBy(() -> store.updateMessages("test", null)).isInstanceOf(IllegalArgumentException.class);

        // Test empty messages list
        log.info("🔍 {} Testing empty messages list...", logPrefix);
        assertThatThrownBy(() -> store.updateMessages("test", Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class);

        log.info("✅ {} Null and empty input handling test completed successfully", logPrefix);
    }

    @Test
    void should_store_and_retrieve_conversation() {
        testStoreAndRetrieveConversation(memoryStore, logger, "[POSTGRESQL]", "postgresql");
    }

    @Test
    void should_update_existing_conversation() {
        testUpdateExistingConversation(memoryStore, logger, "[POSTGRESQL]", "postgresql");
    }

    @Test
    void should_delete_messages() {
        testDeleteMessages(memoryStore, logger, "[POSTGRESQL]", "postgresql");
    }

    @Test
    void should_handle_non_existent_memory_id() {
        testNonExistentMemoryId(memoryStore, logger, "[POSTGRESQL]");
    }

    @Test
    void should_handle_null_and_empty_inputs() {
        testNullAndEmptyInputs(memoryStore, logger, "[POSTGRESQL]");
    }

    /**
     * Nested test class for YugabyteDB Smart Driver chat memory tests.
     * Runs all chat memory tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    @Testcontainers
    class SmartDriverChatMemoryIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverChatMemoryIT.class);
        private static YugabyteDBEngine smartEngine;
        private static ChatMemoryStore smartMemoryStore;

        @BeforeAll
        static void setupSmartDriver() {
            smartLogger.info("🚀 [SMART-DRIVER] Initializing Chat Memory Store with Smart Driver...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            String host = yugabyteContainer.getHost();
            Integer port = yugabyteContainer.getMappedPort(5433);

            smartLogger.info("📋 [SMART-DRIVER] Container details:");
            smartLogger.info("[SMART-DRIVER]   - Host: {}", host);
            smartLogger.info("[SMART-DRIVER]   - Mapped port: {}", port);

            // Create Smart Driver engine
            smartLogger.info("🔧 [SMART-DRIVER] Creating Smart Driver engine...");
            smartEngine = YugabyteDBEngine.builder()
                    .host(host)
                    .port(port)
                    .database(DB_NAME)
                    .username(DB_USER)
                    .password(DB_PASSWORD)
                    .usePostgreSQLDriver(false) // Use Smart Driver
                    .maxPoolSize(10)
                    .build();
            smartLogger.info("✅ [SMART-DRIVER] Smart Driver engine created successfully");

            // Create memory store
            smartLogger.info("🧠 [SMART-DRIVER] Creating chat memory store...");
            smartMemoryStore = YugabyteDBChatMemoryStore.builder()
                    .engine(smartEngine)
                    .tableName("chat_memory_smart")
                    .createTableIfNotExists(true)
                    .build();

            smartLogger.info("✅ [SMART-DRIVER] Chat memory store created successfully");
            smartLogger.info("🎉 [SMART-DRIVER] Chat Memory Store setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER] Starting Chat Memory Store cleanup...");

            if (smartEngine != null) {
                smartLogger.info("[SMART-DRIVER] Closing Smart Driver engine...");
                smartEngine.close();
            }

            smartLogger.info("✅ [SMART-DRIVER] Chat Memory Store cleanup completed");
        }

        @Test
        void should_store_and_retrieve_conversation() {
            testStoreAndRetrieveConversation(smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_update_existing_conversation() {
            testUpdateExistingConversation(smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_delete_messages() {
            testDeleteMessages(smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_handle_non_existent_memory_id() {
            testNonExistentMemoryId(smartMemoryStore, smartLogger, "[SMART-DRIVER]");
        }

        @Test
        void should_handle_null_and_empty_inputs() {
            testNullAndEmptyInputs(smartMemoryStore, smartLogger, "[SMART-DRIVER]");
        }
    }
}
