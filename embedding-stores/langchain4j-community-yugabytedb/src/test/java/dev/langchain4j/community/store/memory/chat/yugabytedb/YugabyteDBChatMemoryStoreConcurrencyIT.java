package dev.langchain4j.community.store.memory.chat.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBEngine;
import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Concurrency and thread safety tests for YugabyteDBChatMemoryStore using TestContainers.
 * Tests concurrent access, thread safety, and race condition handling.
 *
 * Main class tests use PostgreSQL JDBC Driver.
 * Nested SmartDriverConcurrencyIT class tests use YugabyteDB Smart Driver.
 */
class YugabyteDBChatMemoryStoreConcurrencyIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBChatMemoryStoreConcurrencyIT.class);

    private static YugabyteDBEngine engine;
    private static ChatMemoryStore memoryStore;

    // Concurrency test constants
    private static final int CONCURRENT_THREADS = 10;
    private static final int CONCURRENT_OPERATIONS_PER_THREAD = 50;
    private static final int STRESS_TEST_THREADS = 20;
    private static final int STRESS_TEST_OPERATIONS = 25;

    @BeforeAll
    static void setUp() {
        logger.info("🚀 [POSTGRESQL] Initializing Chat Memory Store concurrency tests with PostgreSQL driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        String host = yugabyteContainer.getHost();
        Integer port = yugabyteContainer.getMappedPort(5433);

        logger.info("📋 [POSTGRESQL] Container details:");
        logger.info("[POSTGRESQL]   - Host: {}", host);
        logger.info("[POSTGRESQL]   - Mapped port: {}", port);

        // Create YugabyteDBEngine for testing with higher pool size for concurrency tests
        logger.info("🔧 [POSTGRESQL] Creating YugabyteDBEngine with PostgreSQL driver...");
        engine = YugabyteDBEngine.builder()
                .host(host)
                .port(port)
                .database(DB_NAME)
                .username(DB_USER)
                .password(DB_PASSWORD)
                .usePostgreSQLDriver(true) // Explicitly use PostgreSQL driver
                .maxPoolSize(30) // Higher pool size for concurrency tests
                .build();
        logger.info("✅ [POSTGRESQL] YugabyteDBEngine created successfully");

        // Create memory store
        logger.info("🧠 [POSTGRESQL] Creating chat memory store...");
        memoryStore = YugabyteDBChatMemoryStore.builder()
                .engine(engine)
                .tableName("chat_memory_concurrency_postgresql")
                .createTableIfNotExists(true)
                .build();

        logger.info("✅ [POSTGRESQL] Chat memory store created successfully");
        logger.info("🎉 [POSTGRESQL] Chat Memory Store concurrency test setup completed");
    }

    @AfterAll
    static void tearDown() {
        logger.info("🧹 [POSTGRESQL] Starting Chat Memory Store concurrency test cleanup...");

        if (engine != null) {
            logger.info("[POSTGRESQL] Closing YugabyteDBEngine...");
            engine.close();
        }

        logger.info("✅ [POSTGRESQL] Chat Memory Store concurrency test cleanup completed");
    }

    // ========================================
    // MODULAR HELPER METHODS
    // ========================================

    private static void testConcurrentAccess(
            ChatMemoryStore store, Logger log, String logPrefix, String memoryIdPrefix) {
        log.info(
                "🧪 {} Testing concurrent access with {} threads, {} operations per thread...",
                logPrefix,
                CONCURRENT_THREADS,
                CONCURRENT_OPERATIONS_PER_THREAD);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_THREADS);

        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);
        AtomicLong totalOperationTime = new AtomicLong(0);

        // Create concurrent tasks
        for (int threadId = 0; threadId < CONCURRENT_THREADS; threadId++) {
            final int finalThreadId = threadId;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    log.info(
                            "🔄 {} Thread {} starting {} operations...",
                            logPrefix,
                            finalThreadId,
                            CONCURRENT_OPERATIONS_PER_THREAD);

                    for (int i = 0; i < CONCURRENT_OPERATIONS_PER_THREAD; i++) {
                        try {
                            String memoryId = String.format("%s-thread-%d-op-%d", memoryIdPrefix, finalThreadId, i);
                            List<ChatMessage> messages = Arrays.asList(UserMessage.from(
                                    String.format("Message from thread %d, operation %d", finalThreadId, i)));

                            long opStart = System.currentTimeMillis();

                            // Store, retrieve, and delete
                            store.updateMessages(memoryId, messages);
                            List<ChatMessage> retrieved = store.getMessages(memoryId);
                            store.deleteMessages(memoryId);

                            long opTime = System.currentTimeMillis() - opStart;
                            totalOperationTime.addAndGet(opTime);

                            // Verify operation
                            assertThat(retrieved).hasSize(1);
                            successfulOperations.incrementAndGet();

                        } catch (Exception e) {
                            log.error(
                                    "❌ {} Thread {} operation {} failed: {}",
                                    logPrefix,
                                    finalThreadId,
                                    i,
                                    e.getMessage());
                            failedOperations.incrementAndGet();
                        }
                    }

                    log.info("✅ {} Thread {} completed all operations", logPrefix, finalThreadId);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("❌ {} Thread {} interrupted", logPrefix, finalThreadId);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        log.info("🚀 {} Starting all threads...", logPrefix);
        long concurrencyStart = System.currentTimeMillis();
        startLatch.countDown();

        // Wait for completion
        try {
            boolean completed = completionLatch.await(90, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Concurrency test interrupted", e);
        } finally {
            executor.shutdown();
        }

        long concurrencyTime = System.currentTimeMillis() - concurrencyStart;

        // Calculate and log concurrency metrics
        int totalExpectedOperations = CONCURRENT_THREADS * CONCURRENT_OPERATIONS_PER_THREAD;
        int totalActualOperations = successfulOperations.get() + failedOperations.get();
        double successRate = (double) successfulOperations.get() / totalExpectedOperations * 100;
        double avgOperationTime = totalOperationTime.get() / (double) successfulOperations.get();
        double concurrentOpsPerSecond = (successfulOperations.get() * 3.0) / (concurrencyTime / 1000.0);

        log.info("📊 {} Concurrency test results:", logPrefix);
        log.info("{}   - Threads: {}", logPrefix, CONCURRENT_THREADS);
        log.info("{}   - Operations per thread: {}", logPrefix, CONCURRENT_OPERATIONS_PER_THREAD);
        log.info("{}   - Expected total operations: {}", logPrefix, totalExpectedOperations);
        log.info("{}   - Actual total operations: {}", logPrefix, totalActualOperations);
        log.info("{}   - Successful operations: {}", logPrefix, successfulOperations.get());
        log.info("{}   - Failed operations: {}", logPrefix, failedOperations.get());
        log.info("{}   - Success rate: {:.2f}%", logPrefix, successRate);
        log.info("{}   - Total concurrency time: {}ms", logPrefix, concurrencyTime);
        log.info("{}   - Average operation time: {:.2f}ms", logPrefix, avgOperationTime);
        log.info("{}   - Concurrent operations per second: {:.2f}", logPrefix, concurrentOpsPerSecond);

        // Concurrency assertions
        assertThat(successRate).isGreaterThan(95.0); // At least 95% success rate
        assertThat(failedOperations.get()).isLessThan(totalExpectedOperations / 20); // Less than 5% failures

        log.info("✅ {} Concurrency test completed successfully", logPrefix);
    }

    private static void testConcurrentUpdatesToSameMemoryId(
            ChatMemoryStore store, Logger log, String logPrefix, String sharedMemoryId) {
        log.info("🧪 {} Testing concurrent updates to same memory ID...", logPrefix);

        int concurrentUpdaters = 10;
        int updatesPerThread = 20;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentUpdaters);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentUpdaters);

        AtomicInteger successfulUpdates = new AtomicInteger(0);
        AtomicInteger failedUpdates = new AtomicInteger(0);

        // Create concurrent updater tasks
        for (int threadId = 0; threadId < concurrentUpdaters; threadId++) {
            final int finalThreadId = threadId;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    log.info(
                            "🔄 {} Thread {} starting {} updates to shared memory ID...",
                            logPrefix,
                            finalThreadId,
                            updatesPerThread);

                    for (int i = 0; i < updatesPerThread; i++) {
                        try {
                            List<ChatMessage> messages = Arrays.asList(
                                    UserMessage.from(
                                            String.format("Update from thread %d, iteration %d", finalThreadId, i)),
                                    AiMessage.from(
                                            String.format("Response from thread %d, iteration %d", finalThreadId, i)));

                            store.updateMessages(sharedMemoryId, messages);
                            successfulUpdates.incrementAndGet();

                        } catch (Exception e) {
                            log.error(
                                    "❌ {} Thread {} update {} failed: {}", logPrefix, finalThreadId, i, e.getMessage());
                            failedUpdates.incrementAndGet();
                        }
                    }

                    log.info("✅ {} Thread {} completed all updates", logPrefix, finalThreadId);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("❌ {} Thread {} interrupted", logPrefix, finalThreadId);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads
        log.info("🚀 {} Starting concurrent updates...", logPrefix);
        startLatch.countDown();

        // Wait for completion
        try {
            boolean completed = completionLatch.await(90, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Concurrent updates test interrupted", e);
        } finally {
            executor.shutdown();
        }

        // Verify final state
        List<ChatMessage> finalMessages = store.getMessages(sharedMemoryId);
        log.info("📥 {} Final state: {} messages in shared memory", logPrefix, finalMessages.size());

        int totalExpectedUpdates = concurrentUpdaters * updatesPerThread;
        double updateSuccessRate = (double) successfulUpdates.get() / totalExpectedUpdates * 100;

        log.info("📊 {} Concurrent updates results:", logPrefix);
        log.info("{}   - Concurrent updaters: {}", logPrefix, concurrentUpdaters);
        log.info("{}   - Updates per thread: {}", logPrefix, updatesPerThread);
        log.info("{}   - Total expected updates: {}", logPrefix, totalExpectedUpdates);
        log.info("{}   - Successful updates: {}", logPrefix, successfulUpdates.get());
        log.info("{}   - Failed updates: {}", logPrefix, failedUpdates.get());
        log.info("{}   - Update success rate: {}%", logPrefix, String.format("%.2f", updateSuccessRate));
        log.info("{}   - Final message count: {}", logPrefix, finalMessages.size());

        // Assertions
        assertThat(updateSuccessRate).isGreaterThan(90.0); // At least 90% success rate
        assertThat(finalMessages).hasSize(2); // Should have exactly 2 messages (last update wins)

        // Cleanup
        store.deleteMessages(sharedMemoryId);

        log.info("✅ {} Concurrent updates to same memory ID test completed successfully", logPrefix);
    }

    private static void testMixedConcurrentOperations(
            ChatMemoryStore store, Logger log, String logPrefix, String memoryIdPrefix) {
        log.info("🧪 {} Testing mixed concurrent operations (read/write/delete)...", logPrefix);

        int readerThreads = 20;
        int writerThreads = 20;
        int deleterThreads = 12;
        int operationsPerThread = 120;

        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads + deleterThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(readerThreads + writerThreads + deleterThreads);

        AtomicInteger readOperations = new AtomicInteger(0);
        AtomicInteger writeOperations = new AtomicInteger(0);
        AtomicInteger deleteOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);

        // Create reader threads
        for (int i = 0; i < readerThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    log.info("📖 {} Reader thread {} starting...", logPrefix, threadId);

                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            String memoryId = memoryIdPrefix + "-mixed-ops-" + (j % 20);
                            store.getMessages(memoryId);
                            readOperations.incrementAndGet();
                        } catch (Exception e) {
                            failedOperations.incrementAndGet();
                        }
                    }

                    log.info("✅ {} Reader thread {} completed", logPrefix, threadId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Create writer threads
        for (int i = 0; i < writerThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    log.info("✍️ {} Writer thread {} starting...", logPrefix, threadId);

                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            String memoryId =
                                    memoryIdPrefix + "-mixed-ops-" + ((threadId * operationsPerThread + j) % 50);
                            List<ChatMessage> messages =
                                    Arrays.asList(UserMessage.from(String.format("Writer %d message %d", threadId, j)));
                            store.updateMessages(memoryId, messages);
                            writeOperations.incrementAndGet();
                        } catch (Exception e) {
                            failedOperations.incrementAndGet();
                        }
                    }

                    log.info("✅ {} Writer thread {} completed", logPrefix, threadId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Create deleter threads
        for (int i = 0; i < deleterThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Wait a bit to let writers create some data
                    Thread.sleep(1000);
                    log.info("🗑️ {} Deleter thread {} starting...", logPrefix, threadId);

                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            String memoryId = memoryIdPrefix + "-mixed-ops-" + (j % 30);
                            store.deleteMessages(memoryId);
                            deleteOperations.incrementAndGet();
                        } catch (Exception e) {
                            failedOperations.incrementAndGet();
                        }
                    }

                    log.info("✅ {} Deleter thread {} completed", logPrefix, threadId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads
        log.info("🚀 {} Starting mixed concurrent operations...", logPrefix);
        startLatch.countDown();

        // Wait for completion
        try {
            boolean completed = completionLatch.await(120, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Mixed concurrent operations test interrupted", e);
        } finally {
            executor.shutdown();
        }

        int totalOperations =
                readOperations.get() + writeOperations.get() + deleteOperations.get() + failedOperations.get();
        double successRate = (double) (totalOperations - failedOperations.get()) / totalOperations * 100;

        log.info("📊 {} Mixed concurrent operations results:", logPrefix);
        log.info("{}   - Reader threads: {}", logPrefix, readerThreads);
        log.info("{}   - Writer threads: {}", logPrefix, writerThreads);
        log.info("{}   - Deleter threads: {}", logPrefix, deleterThreads);
        log.info("{}   - Operations per thread: {}", logPrefix, operationsPerThread);
        log.info("{}   - Read operations: {}", logPrefix, readOperations.get());
        log.info("{}   - Write operations: {}", logPrefix, writeOperations.get());
        log.info("{}   - Delete operations: {}", logPrefix, deleteOperations.get());
        log.info("{}   - Failed operations: {}", logPrefix, failedOperations.get());
        log.info("{}   - Total operations: {}", logPrefix, totalOperations);
        log.info("{}   - Success rate: {:.2f}%", logPrefix, successRate);

        // Assertions
        assertThat(successRate).isGreaterThan(90.0); // At least 90% success rate
        assertThat(readOperations.get()).isGreaterThan(0);
        assertThat(writeOperations.get()).isGreaterThan(0);
        assertThat(deleteOperations.get()).isGreaterThan(0);

        log.info("✅ {} Mixed concurrent operations test completed successfully", logPrefix);
    }

    private static void testStressWithHighConcurrency(
            ChatMemoryStore store, Logger log, String logPrefix, String memoryIdPrefix) {
        log.info(
                "🧪 {} Testing high concurrency stress with {} threads, {} operations per thread...",
                logPrefix,
                STRESS_TEST_THREADS,
                STRESS_TEST_OPERATIONS);

        ExecutorService executor = Executors.newFixedThreadPool(STRESS_TEST_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(STRESS_TEST_THREADS);

        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        // Create stress test tasks
        for (int threadId = 0; threadId < STRESS_TEST_THREADS; threadId++) {
            final int finalThreadId = threadId;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    long threadStart = System.currentTimeMillis();
                    log.info("{} Thread {} starting stress operations...", logPrefix, finalThreadId);

                    for (int i = 0; i < STRESS_TEST_OPERATIONS; i++) {
                        try {
                            String memoryId = String.format("%s-stress-%d-%d", memoryIdPrefix, finalThreadId, i);

                            // Create varied conversation lengths
                            List<ChatMessage> messages = Arrays.asList(
                                    UserMessage.from(String.format(
                                            "Stress test message from thread %d, op %d", finalThreadId, i)),
                                    AiMessage.from(String.format(
                                            "Stress test response from thread %d, op %d", finalThreadId, i)));

                            // Perform multiple operations per iteration
                            store.updateMessages(memoryId, messages);
                            List<ChatMessage> retrieved = store.getMessages(memoryId);
                            store.updateMessages(memoryId, messages); // Update again
                            store.deleteMessages(memoryId);

                            assertThat(retrieved).hasSize(2);
                            successfulOperations.addAndGet(4); // 4 operations per iteration
                            totalOperations.addAndGet(4);

                        } catch (Exception e) {
                            log.warn(
                                    "⚠️ {} Thread {} operation {} failed: {}",
                                    logPrefix,
                                    finalThreadId,
                                    i,
                                    e.getMessage());
                            failedOperations.addAndGet(4);
                            totalOperations.addAndGet(4);
                        }
                    }

                    long threadTime = System.currentTimeMillis() - threadStart;
                    totalTime.addAndGet(threadTime);
                    log.info("✅ {} Thread {} completed in {}ms", logPrefix, finalThreadId, threadTime);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("❌ {} Thread {} interrupted", logPrefix, finalThreadId);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start stress test
        log.info("🚀 {} Starting stress test...", logPrefix);
        long stressStart = System.currentTimeMillis();
        startLatch.countDown();

        // Wait for completion
        try {
            boolean completed = completionLatch.await(150, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Stress test interrupted", e);
        } finally {
            executor.shutdown();
        }

        long stressTime = System.currentTimeMillis() - stressStart;
        double successRate = (double) successfulOperations.get() / totalOperations.get() * 100;
        double avgThreadTime = (double) totalTime.get() / STRESS_TEST_THREADS;
        double operationsPerSecond = (double) successfulOperations.get() / (stressTime / 1000.0);

        log.info("📊 {} Stress test results:", logPrefix);
        log.info("{}   - Threads: {}", logPrefix, STRESS_TEST_THREADS);
        log.info("{}   - Operations per thread: {}", logPrefix, STRESS_TEST_OPERATIONS * 4);
        log.info("{}   - Total operations: {}", logPrefix, totalOperations.get());
        log.info("{}   - Successful operations: {}", logPrefix, successfulOperations.get());
        log.info("{}   - Failed operations: {}", logPrefix, failedOperations.get());
        log.info("{}   - Success rate: {:.2f}%", logPrefix, successRate);
        log.info("{}   - Total stress time: {}ms", logPrefix, stressTime);
        log.info("{}   - Average thread time: {:.2f}ms", logPrefix, avgThreadTime);
        log.info("{}   - Operations per second: {:.2f}", logPrefix, operationsPerSecond);

        // Stress test assertions
        assertThat(successRate).isGreaterThan(85.0); // At least 85% success rate under stress
        assertThat(operationsPerSecond).isGreaterThan(5.0); // Should handle at least 5 ops/sec under stress

        log.info("✅ {} Stress test completed successfully", logPrefix);
    }

    @Test
    @Timeout(120)
    void should_handle_concurrent_access() {
        testConcurrentAccess(memoryStore, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    @Timeout(120)
    void should_handle_concurrent_updates_to_same_memory_id() {
        testConcurrentUpdatesToSameMemoryId(memoryStore, logger, "[POSTGRESQL]", "pg-shared-memory-test");
    }

    @Test
    @Timeout(150)
    void should_handle_mixed_concurrent_operations() {
        testMixedConcurrentOperations(memoryStore, logger, "[POSTGRESQL]", "pg");
    }

    @Test
    @Timeout(180)
    void should_handle_stress_test_with_high_concurrency() {
        testStressWithHighConcurrency(memoryStore, logger, "[POSTGRESQL]", "pg");
    }

    /**
     * Nested test class for YugabyteDB Smart Driver concurrency tests.
     * Runs all concurrency tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    @Testcontainers
    class SmartDriverConcurrencyIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverConcurrencyIT.class);
        private static YugabyteDBEngine smartEngine;
        private static ChatMemoryStore smartMemoryStore;

        @BeforeAll
        static void setupSmartDriver() {
            smartLogger.info("🚀 [SMART-DRIVER] Initializing Chat Memory Store concurrency tests with Smart Driver...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            String host = yugabyteContainer.getHost();
            Integer port = yugabyteContainer.getMappedPort(5433);

            smartLogger.info("📋 [SMART-DRIVER] Container details:");
            smartLogger.info("[SMART-DRIVER]   - Host: {}", host);
            smartLogger.info("[SMART-DRIVER]   - Mapped port: {}", port);

            // Create Smart Driver engine with higher pool size for concurrency tests
            smartLogger.info("🔧 [SMART-DRIVER] Creating Smart Driver engine...");
            smartEngine = YugabyteDBEngine.builder()
                    .host(host)
                    .port(port)
                    .database(DB_NAME)
                    .username(DB_USER)
                    .password(DB_PASSWORD)
                    .usePostgreSQLDriver(false) // Use Smart Driver
                    .maxPoolSize(30) // Higher pool size for concurrency tests
                    .build();
            smartLogger.info("✅ [SMART-DRIVER] Smart Driver engine created successfully");

            // Create memory store
            smartLogger.info("🧠 [SMART-DRIVER] Creating chat memory store...");
            smartMemoryStore = YugabyteDBChatMemoryStore.builder()
                    .engine(smartEngine)
                    .tableName("chat_memory_concurrency_smart")
                    .createTableIfNotExists(true)
                    .build();

            smartLogger.info("✅ [SMART-DRIVER] Chat memory store created successfully");
            smartLogger.info("🎉 [SMART-DRIVER] Chat Memory Store concurrency test setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER] Starting Chat Memory Store concurrency test cleanup...");

            if (smartEngine != null) {
                smartLogger.info("[SMART-DRIVER] Closing Smart Driver engine...");
                smartEngine.close();
            }

            smartLogger.info("✅ [SMART-DRIVER] Chat Memory Store concurrency test cleanup completed");
        }

        @Test
        @Timeout(120)
        void should_handle_concurrent_access() {
            testConcurrentAccess(smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        @Timeout(120)
        void should_handle_concurrent_updates_to_same_memory_id() {
            testConcurrentUpdatesToSameMemoryId(
                    smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart-shared-memory-test");
        }

        @Test
        @Timeout(150)
        void should_handle_mixed_concurrent_operations() {
            testMixedConcurrentOperations(smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart");
        }

        @Test
        @Timeout(180)
        void should_handle_stress_test_with_high_concurrency() {
            testStressWithHighConcurrency(smartMemoryStore, smartLogger, "[SMART-DRIVER]", "smart");
        }
    }
}
