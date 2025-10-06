package dev.langchain4j.community.store.memory.chat.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBEngine;
import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TTL (Time-To-Live) and cleanup functionality tests for YugabyteDBChatMemoryStore using TestContainers.
 * Tests message expiration, cleanup operations, and TTL-related behavior.
 *
 * Main class tests use PostgreSQL JDBC Driver.
 * Nested SmartDriverTTLIT class tests use YugabyteDB Smart Driver.
 */
class YugabyteDBChatMemoryStoreTTLIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBChatMemoryStoreTTLIT.class);

    private static YugabyteDBEngine engine;
    private static ChatMemoryStore memoryStoreWithTTL;
    private static ChatMemoryStore memoryStoreWithoutTTL;
    private static YugabyteDBChatMemoryStore ttlStore; // For cleanup operations

    @BeforeAll
    static void setUp() {
        logger.info("🚀 [POSTGRESQL] Initializing Chat Memory Store TTL tests with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        String host = yugabyteContainer.getHost();
        Integer port = yugabyteContainer.getMappedPort(5433);

        logger.info("📋 [POSTGRESQL] Container details:");
        logger.info("[POSTGRESQL]   - Host: {}", host);
        logger.info("[POSTGRESQL]   - Mapped port: {}", port);

        // Create YugabyteDBEngine for testing with PostgreSQL driver
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

        // Create memory stores - one with TTL, one without
        logger.info("🧠 [POSTGRESQL] Creating chat memory stores...");

        ttlStore = YugabyteDBChatMemoryStore.builder()
                .engine(engine)
                .tableName("chat_memory_ttl_postgresql")
                .ttl(Duration.ofSeconds(5)) // Short TTL for testing
                .createTableIfNotExists(true)
                .build();
        memoryStoreWithTTL = ttlStore;

        memoryStoreWithoutTTL = YugabyteDBChatMemoryStore.builder()
                .engine(engine)
                .tableName("chat_memory_no_ttl_postgresql")
                .createTableIfNotExists(true)
                .build();

        logger.info("✅ [POSTGRESQL] Chat memory stores created successfully");
        logger.info("🎉 [POSTGRESQL] Chat Memory Store TTL test setup completed");
    }

    @AfterAll
    static void tearDown() {
        logger.info("🧹 [POSTGRESQL] Starting Chat Memory Store TTL test cleanup...");

        if (engine != null) {
            logger.info("[POSTGRESQL] Closing YugabyteDBEngine...");
            engine.close();
        }

        logger.info("✅ [POSTGRESQL] Chat Memory Store TTL test cleanup completed");
    }

    // ========================================
    // MODULAR HELPER METHODS
    // ========================================

    private static void testTTLExpiration(
            ChatMemoryStore ttlMemoryStore,
            YugabyteDBChatMemoryStore cleanupStore,
            Logger log,
            String logPrefix,
            String memoryIdPrefix)
            throws InterruptedException {
        log.info("🧪 {} Testing TTL expiration functionality...", logPrefix);

        // Given
        String memoryId = memoryIdPrefix + "-ttl-test";
        List<ChatMessage> messages = Arrays.asList(
                UserMessage.from("This message should expire"), AiMessage.from("This response should also expire"));

        // When - Store message with TTL
        log.info("💾 {} Storing messages with 5-second TTL...", logPrefix);
        ttlMemoryStore.updateMessages(memoryId, messages);

        // Verify messages exist immediately
        List<ChatMessage> immediateRetrieve = ttlMemoryStore.getMessages(memoryId);
        log.info("📥 {} Immediate retrieval: {} message(s)", logPrefix, immediateRetrieve.size());
        assertThat(immediateRetrieve).hasSize(2);

        // Wait for TTL to expire
        log.info("⏳ {} Waiting 6 seconds for TTL expiration...", logPrefix);
        Thread.sleep(6000);

        // Clean up expired messages
        log.info("🧹 {} Running cleanup for expired messages...", logPrefix);
        int cleanedUp = cleanupStore.cleanupExpiredMessages();
        log.info("🧹 {} Cleaned up {} expired message records", logPrefix, cleanedUp);
        assertThat(cleanedUp).isGreaterThanOrEqualTo(0);

        // Verify messages have expired
        List<ChatMessage> afterExpiration = ttlMemoryStore.getMessages(memoryId);
        log.info("📥 {} After expiration: {} message(s)", logPrefix, afterExpiration.size());
        assertThat(afterExpiration).isEmpty();

        log.info("✅ {} TTL expiration test completed successfully", logPrefix);
    }

    private static void testMessagesWithoutTTL(
            ChatMemoryStore noTTLStore, Logger log, String logPrefix, String memoryIdPrefix)
            throws InterruptedException {
        log.info("🧪 {} Testing that messages without TTL do not expire...", logPrefix);

        // Given
        String memoryId = memoryIdPrefix + "-no-ttl-test";
        List<ChatMessage> messages = Arrays.asList(
                UserMessage.from("This message should NOT expire"),
                AiMessage.from("This response should also NOT expire"));

        // When - Store message without TTL
        log.info("💾 {} Storing messages without TTL...", logPrefix);
        noTTLStore.updateMessages(memoryId, messages);

        // Verify messages exist immediately
        List<ChatMessage> immediateRetrieve = noTTLStore.getMessages(memoryId);
        log.info("📥 {} Immediate retrieval: {} message(s)", logPrefix, immediateRetrieve.size());
        assertThat(immediateRetrieve).hasSize(2);

        // Wait longer than the TTL would be
        log.info("⏳ {} Waiting 8 seconds (longer than TTL would be)...", logPrefix);
        Thread.sleep(8000);

        // Verify messages still exist
        List<ChatMessage> afterWait = noTTLStore.getMessages(memoryId);
        log.info("📥 {} After waiting: {} message(s)", logPrefix, afterWait.size());
        assertThat(afterWait).hasSize(2);

        // Cleanup
        noTTLStore.deleteMessages(memoryId);

        log.info("✅ {} Messages without TTL persistence test completed successfully", logPrefix);
    }

    private static void testCleanupWithNoExpiredMessages(
            ChatMemoryStore ttlMemoryStore,
            YugabyteDBChatMemoryStore cleanupStore,
            Logger log,
            String logPrefix,
            String memoryIdPrefix) {
        log.info("🧪 {} Testing cleanup when no messages are expired...", logPrefix);

        // Store fresh messages
        String memoryId = memoryIdPrefix + "-fresh-messages-test";
        List<ChatMessage> messages = Arrays.asList(
                UserMessage.from("Fresh message that should not expire yet"),
                AiMessage.from("Fresh response that should not expire yet"));

        log.info("💾 {} Storing fresh messages...", logPrefix);
        ttlMemoryStore.updateMessages(memoryId, messages);

        // Verify messages exist
        List<ChatMessage> beforeCleanup = ttlMemoryStore.getMessages(memoryId);
        log.info("📥 {} Before cleanup: {} message(s)", logPrefix, beforeCleanup.size());
        assertThat(beforeCleanup).hasSize(2);

        // Run cleanup immediately (no messages should be expired)
        log.info("🧹 {} Running cleanup on fresh messages...", logPrefix);
        int cleanedUp = cleanupStore.cleanupExpiredMessages();
        log.info("🧹 {} Cleaned up {} expired records (should be 0)", logPrefix, cleanedUp);
        assertThat(cleanedUp).isEqualTo(0);

        // Verify messages still exist
        List<ChatMessage> afterCleanup = ttlMemoryStore.getMessages(memoryId);
        log.info("📥 {} After cleanup: {} message(s)", logPrefix, afterCleanup.size());
        assertThat(afterCleanup).hasSize(2);

        // Cleanup test data
        ttlMemoryStore.deleteMessages(memoryId);

        log.info("✅ {} Cleanup with no expired messages test completed successfully", logPrefix);
    }

    private static void testCleanupOnStoreWithoutTTL(
            ChatMemoryStore noTTLStore, Logger log, String logPrefix, String memoryIdPrefix) {
        log.info("🧪 {} Testing cleanup on store without TTL configuration...", logPrefix);

        // Store messages in non-TTL store
        String memoryId = memoryIdPrefix + "-no-ttl-cleanup-test";
        List<ChatMessage> messages = Arrays.asList(UserMessage.from("Message in non-TTL store"));

        log.info("💾 {} Storing message in non-TTL store...", logPrefix);
        noTTLStore.updateMessages(memoryId, messages);

        // Verify message exists
        List<ChatMessage> beforeCleanup = noTTLStore.getMessages(memoryId);
        log.info("📥 {} Before cleanup: {} message(s)", logPrefix, beforeCleanup.size());
        assertThat(beforeCleanup).hasSize(1);

        // Try to run cleanup (should handle gracefully)
        log.info("🧹 {} Attempting cleanup on non-TTL store...", logPrefix);
        if (noTTLStore instanceof YugabyteDBChatMemoryStore) {
            YugabyteDBChatMemoryStore nonTTLStore = (YugabyteDBChatMemoryStore) noTTLStore;
            int cleanedUp = nonTTLStore.cleanupExpiredMessages();
            log.info("🧹 {} Cleanup result: {} (should be 0 for non-TTL store)", logPrefix, cleanedUp);
            assertThat(cleanedUp).isEqualTo(0);
        }

        // Verify message still exists
        List<ChatMessage> afterCleanup = noTTLStore.getMessages(memoryId);
        log.info("📥 {} After cleanup: {} message(s)", logPrefix, afterCleanup.size());
        assertThat(afterCleanup).hasSize(1);

        // Cleanup test data
        noTTLStore.deleteMessages(memoryId);

        log.info("✅ {} Cleanup on non-TTL store test completed successfully", logPrefix);
    }

    // ========================================
    // POSTGRESQL DRIVER TESTS
    // ========================================

    @Test
    @Timeout(30)
    void should_handle_ttl_expiration() throws InterruptedException {
        testTTLExpiration(memoryStoreWithTTL, ttlStore, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    void should_not_expire_messages_without_ttl() throws InterruptedException {
        testMessagesWithoutTTL(memoryStoreWithoutTTL, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    @Timeout(30)
    void should_handle_cleanup_with_no_expired_messages() {
        testCleanupWithNoExpiredMessages(memoryStoreWithTTL, ttlStore, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    void should_handle_cleanup_on_store_without_ttl() {
        testCleanupOnStoreWithoutTTL(memoryStoreWithoutTTL, logger, "[POSTGRESQL]", "pg");
    }

    /**
     * Nested test class for YugabyteDB Smart Driver TTL tests.
     * Runs all TTL tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    @Testcontainers
    class SmartDriverTTLIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverTTLIT.class);
        private static YugabyteDBEngine smartEngine;
        private static ChatMemoryStore smartMemoryStoreWithTTL;
        private static ChatMemoryStore smartMemoryStoreWithoutTTL;
        private static YugabyteDBChatMemoryStore smartTTLStore;

        @BeforeAll
        static void setupSmartDriver() {
            smartLogger.info("🚀 [SMART-DRIVER] Initializing Chat Memory Store TTL tests with Smart Driver...");
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

            // Create memory stores - one with TTL, one without
            smartLogger.info("🧠 [SMART-DRIVER] Creating chat memory stores...");

            smartTTLStore = YugabyteDBChatMemoryStore.builder()
                    .engine(smartEngine)
                    .tableName("chat_memory_ttl_smart")
                    .ttl(Duration.ofSeconds(5)) // Short TTL for testing
                    .createTableIfNotExists(true)
                    .build();
            smartMemoryStoreWithTTL = smartTTLStore;

            smartMemoryStoreWithoutTTL = YugabyteDBChatMemoryStore.builder()
                    .engine(smartEngine)
                    .tableName("chat_memory_no_ttl_smart")
                    .createTableIfNotExists(true)
                    .build();

            smartLogger.info("✅ [SMART-DRIVER] Chat memory stores created successfully");
            smartLogger.info("🎉 [SMART-DRIVER] Chat Memory Store TTL test setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER] Starting Chat Memory Store TTL test cleanup...");

            if (smartEngine != null) {
                smartLogger.info("[SMART-DRIVER] Closing Smart Driver engine...");
                smartEngine.close();
            }

            smartLogger.info("✅ [SMART-DRIVER] Chat Memory Store TTL test cleanup completed");
        }

        @Test
        @Timeout(30)
        void should_handle_ttl_expiration() throws InterruptedException {
            testTTLExpiration(smartMemoryStoreWithTTL, smartTTLStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_not_expire_messages_without_ttl() throws InterruptedException {
            testMessagesWithoutTTL(smartMemoryStoreWithoutTTL, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        @Timeout(30)
        void should_handle_cleanup_with_no_expired_messages() {
            testCleanupWithNoExpiredMessages(
                    smartMemoryStoreWithTTL, smartTTLStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        void should_handle_cleanup_on_store_without_ttl() {
            testCleanupOnStoreWithoutTTL(smartMemoryStoreWithoutTTL, smartLogger, "[SMART-DRIVER]", "smart");
        }
    }
}
