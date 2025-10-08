package dev.langchain4j.community.store.embedding.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Optional integration tests that demonstrate YugabyteDB with actual LLM integration.
 *
 * These tests only run when LLM API keys are available as environment variables.
 * They show how YugabyteDB embedding store would work in a real RAG pipeline
 * with actual language models.
 *
 * Main class tests use PostgreSQL JDBC Driver.
 * Nested SmartDriverLLMIT class tests use YugabyteDB Smart Driver.
 *
 * To run these tests, set one of:
 * - OPENAI_API_KEY=your_openai_key
 * - ANTHROPIC_API_KEY=your_claude_key
 * - OLLAMA_BASE_URL=http://localhost:11434 (for local Ollama)
 *
 * Note: These tests will make actual API calls and may incur costs.
 */
class YugabyteDBWithActualLLMIT extends YugabyteDBTestBase {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBWithActualLLMIT.class);

    // Ollama container management
    private static GenericContainer<?> ollamaContainer;
    private static boolean ollamaContainerCreated = false;
    private static String ollamaBaseUrl;

    @BeforeAll
    @SuppressWarnings("resource")
    static void setupOllama() {
        logger.info("🔍 [OLLAMA-SETUP] Checking for existing Ollama instance...");

        // Check if Ollama is already running
        String existingOllamaUrl = System.getenv("OLLAMA_BASE_URL");
        if (existingOllamaUrl != null && isOllamaRunning(existingOllamaUrl)) {
            logger.info("✅ [OLLAMA-SETUP] Found existing Ollama instance at: {}", existingOllamaUrl);
            ollamaBaseUrl = existingOllamaUrl;
            ollamaContainerCreated = false;
            return;
        }

        // Check default localhost:11434
        if (isOllamaRunning("http://127.0.0.1:11434")) {
            logger.info("✅ [OLLAMA-SETUP] Found existing Ollama instance at: http://127.0.0.1:11434");
            ollamaBaseUrl = "http://127.0.0.1:11434";
            ollamaContainerCreated = false;
            return;
        }

        // Create Ollama container
        logger.info("🚀 [OLLAMA-SETUP] No existing Ollama found, creating test container...");
        DockerImageName ollamaImage = DockerImageName.parse("ollama/ollama:latest");
        ollamaContainer = new GenericContainer<>(ollamaImage)
                .withExposedPorts(11434)
                .withCommand("serve")
                .withEnv("OLLAMA_HOST", "0.0.0.0")
                .withEnv("OLLAMA_ORIGINS", "*")
                .waitingFor(Wait.forHttp("/api/tags").withStartupTimeout(Duration.ofMinutes(10)));

        ollamaContainer.start();
        ollamaBaseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
        ollamaContainerCreated = true;

        logger.info("✅ [OLLAMA-SETUP] Ollama container started at: {}", ollamaBaseUrl);

        // Pull Claude-style and tinyllama models
        logger.info("📥 [OLLAMA-SETUP] Pulling Claude-style and tinyllama models...");
        try {
            // Pull Llama 3.1 with Claude system prompt for the Claude test
            ollamaContainer.execInContainer("ollama", "pull", "incept5/llama3.1-claude");
            logger.info("✅ [OLLAMA-SETUP] Llama 3.1 Claude model pulled successfully");

            // Pull tinyllama for the Ollama test
            ollamaContainer.execInContainer("ollama", "pull", "tinyllama");
            logger.info("✅ [OLLAMA-SETUP] tinyllama model pulled successfully");

            // Wait for models to be fully loaded
            logger.info("⏳ [OLLAMA-SETUP] Waiting for models to be fully loaded...");
            Thread.sleep(20000); // Wait 20 seconds for models to be ready (Claude model is larger)
            logger.info("✅ [OLLAMA-SETUP] Models are ready for inference");
        } catch (Exception e) {
            logger.warn("⚠️ [OLLAMA-SETUP] Warning: Failed to pull models: {}", e.getMessage());
        }
    }

    @AfterAll
    static void cleanupOllama() {
        if (ollamaContainerCreated && ollamaContainer != null) {
            logger.info("🧹 [OLLAMA-CLEANUP] Removing Ollama container and all data...");
            ollamaContainer.stop();
            ollamaContainer.close();
            logger.info("✅ [OLLAMA-CLEANUP] Ollama container and all data removed");
        } else {
            logger.info("ℹ️ [OLLAMA-CLEANUP] No Ollama container to clean up (using existing instance)");
        }
    }

    private static boolean isOllamaRunning(String url) {
        try {
            URL ollamaUrl = new URL(url + "/api/tags");
            HttpURLConnection connection = (HttpURLConnection) ollamaUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    @Disabled("OpenAI API quota exceeded - disable to avoid test failures")
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void should_work_with_openai_in_rag_pipeline() {
        logger.info("🚀 [POSTGRESQL] Testing OpenAI RAG pipeline with PostgreSQL JDBC Driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        YugabyteDBEmbeddingStore store = createStore("openai_rag_test");
        testOpenAIRAGPipeline(store, "[POSTGRESQL]");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
    void should_work_with_claude_in_rag_pipeline() {
        logger.info("🚀 [POSTGRESQL] Testing Claude RAG pipeline with PostgreSQL JDBC Driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        YugabyteDBEmbeddingStore store = createStore("claude_rag_test");
        testClaudeRAGPipeline(store, "[POSTGRESQL]");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
    void should_work_with_ollama_in_rag_pipeline() {
        logger.info("🚀 [POSTGRESQL] Testing Ollama RAG pipeline with PostgreSQL JDBC Driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        YugabyteDBEmbeddingStore store = createStore("ollama_rag_test");
        testOllamaRAGPipeline(store, "[POSTGRESQL]");
    }

    @Test
    void should_demonstrate_rag_context_quality() {
        logger.info("🚀 [POSTGRESQL] Testing RAG context quality with PostgreSQL JDBC Driver...");
        logger.info("🔧 [POSTGRESQL] Driver Type: PostgreSQL JDBC Driver (org.postgresql.Driver)");

        YugabyteDBEmbeddingStore store = createStore("context_quality_test");
        testRAGContextQuality(store, "[POSTGRESQL]");
    }

    /**
     * Nested test class for YugabyteDB Smart Driver LLM integration tests.
     * Runs the same LLM tests using Smart Driver for comprehensive coverage.
     */
    @Nested
    @Testcontainers
    static class SmartDriverLLMIT {

        private static final Logger smartLogger = LoggerFactory.getLogger(SmartDriverLLMIT.class);
        private static YugabyteDBEngine smartEngine;

        private static void setupSmartDriver() {
            if (smartEngine != null) {
                return; // Already initialized
            }

            smartLogger.info("🚀 [SMART-DRIVER-LLM] Initializing Smart Driver for LLM integration tests...");
            smartLogger.info(
                    "🔧 [SMART-DRIVER-LLM] Driver Type: YugabyteDB Smart Driver (com.yugabyte.ysql.YBClusterAwareDataSource)");

            // Ensure container is started before accessing ports
            if (!yugabyteContainer.isRunning()) {
                throw new IllegalStateException(
                        "YugabyteDB container is not running. Make sure the parent test class setup is complete.");
            }

            String host = yugabyteContainer.getHost();
            int port = yugabyteContainer.getMappedPort(5433);

            smartLogger.info("📋 [SMART-DRIVER-LLM] Container details:");
            smartLogger.info("[SMART-DRIVER-LLM]   - Host: {}", host);
            smartLogger.info("[SMART-DRIVER-LLM]   - Port: {}", port);
            smartLogger.info("[SMART-DRIVER-LLM]   - Database: {}", DB_NAME);

            // Create Smart Driver engine
            smartLogger.info("🔧 [SMART-DRIVER-LLM] Creating Smart Driver engine...");
            smartEngine = YugabyteDBEngine.builder()
                    .host(host)
                    .port(port)
                    .database(DB_NAME)
                    .username(DB_USER)
                    .password(DB_PASSWORD)
                    .usePostgreSQLDriver(false) // Use Smart Driver
                    .maxPoolSize(10)
                    .build();
            smartLogger.info("✅ [SMART-DRIVER-LLM] Smart Driver engine created successfully");
            smartLogger.info("✅ [SMART-DRIVER-LLM] Smart Driver LLM test setup completed");
        }

        @AfterAll
        static void cleanupSmartDriver() {
            smartLogger.info("🧹 [SMART-DRIVER-LLM] Starting Smart Driver cleanup...");
            if (smartEngine != null) {
                smartLogger.info("[SMART-DRIVER-LLM] Closing Smart Driver engine...");
                smartEngine.close();
            }
            smartLogger.info("✅ [SMART-DRIVER-LLM] Smart Driver cleanup completed");
        }

        @Test
        @Disabled("OpenAI API quota exceeded - disable to avoid test failures")
        @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
        void should_work_with_openai_in_rag_pipeline() {
            setupSmartDriver();
            smartLogger.info("🚀 [SMART-DRIVER] Testing OpenAI RAG pipeline with Smart Driver...");

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("openai_rag_smart")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();

            testOpenAIRAGPipeline(store, "[SMART-DRIVER]");
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
        void should_work_with_claude_in_rag_pipeline() {
            setupSmartDriver();
            smartLogger.info("🚀 [SMART-DRIVER] Testing Claude RAG pipeline with Smart Driver...");

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("claude_rag_smart")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();

            testClaudeRAGPipeline(store, "[SMART-DRIVER]");
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
        void should_work_with_ollama_in_rag_pipeline() {
            setupSmartDriver();
            smartLogger.info("🚀 [SMART-DRIVER] Testing Ollama RAG pipeline with Smart Driver...");

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("ollama_rag_smart")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();

            testOllamaRAGPipeline(store, "[SMART-DRIVER]");
        }

        @Test
        void should_demonstrate_rag_context_quality() {
            setupSmartDriver();
            smartLogger.info("🚀 [SMART-DRIVER] Testing RAG context quality with Smart Driver...");

            YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
                    .engine(smartEngine)
                    .tableName("context_quality_smart")
                    .dimension(384)
                    .metricType(MetricType.COSINE)
                    .createTableIfNotExists(true)
                    .build();

            testRAGContextQuality(store, "[SMART-DRIVER]");
        }
    }

    // ========================================
    // Modular Helper Methods for Test Logic
    // ========================================

    /**
     * Test OpenAI RAG pipeline with the provided store.
     * @param store The embedding store to test
     * @param logPrefix Log prefix for driver identification (e.g., "[POSTGRESQL]" or "[SMART-DRIVER]")
     */
    private static void testOpenAIRAGPipeline(YugabyteDBEmbeddingStore store, String logPrefix) {
        // This test demonstrates integration with OpenAI in a RAG pipeline

        ChatModel llm = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // Store knowledge base
        String knowledge = "YugabyteDB is a distributed SQL database that provides PostgreSQL compatibility "
                + "with horizontal scalability and high availability across multiple regions.";
        TextSegment segment = TextSegment.from(knowledge, new Metadata().put("source", "docs"));
        Embedding embedding = embeddingModel.embed(segment).content();
        String id = store.add(embedding, segment);

        // User question
        String question = "How does YugabyteDB achieve high availability?";

        // Retrieve context
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .build());

        // Build prompt with context
        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            context.append(match.embedded().text()).append("\n");
        }

        String prompt =
                String.format("Based on this context:\n%s\n\nAnswer the question: %s", context.toString(), question);

        // Get LLM response
        String response = llm.chat(prompt);

        // Verify response
        assertThat(response).isNotBlank();
        assertThat(response.toLowerCase()).containsAnyOf("availability", "distributed", "regions");

        logger.info("📝 {} Question: {}", logPrefix, question);
        logger.info("📚 {} Context: {}", logPrefix, context);
        logger.info("🤖 {} OpenAI Response: {}", logPrefix, response);
        logger.info("✅ {} OpenAI RAG pipeline test completed successfully", logPrefix);

        // Cleanup
        store.removeAll(List.of(id));
    }

    /**
     * Test Claude RAG pipeline with the provided store.
     * @param store The embedding store to test
     * @param logPrefix Log prefix for driver identification (e.g., "[POSTGRESQL]" or "[SMART-DRIVER]")
     */
    private static void testClaudeRAGPipeline(YugabyteDBEmbeddingStore store, String logPrefix) {
        logger.info("=== {} TESTING CLAUDE MODEL WITH RAG ===", logPrefix);

        // Use Ollama with a Claude-compatible model (Llama 3.1 with Claude system prompt)
        ChatModel claude = OllamaChatModel.builder()
                .modelName("incept5/llama3.1-claude")
                .baseUrl(ollamaBaseUrl)
                .timeout(Duration.ofMinutes(3))
                .build();

        // Store knowledge base about a fictional 2025 movie
        String knowledge =
                "Neural Nexus 2025 is a mind-bending psychological thriller directed by Sofia Rodriguez, starring Scarlett Johansson as Dr. Maya Chen, a neuroscientist who develops a neural interface that allows direct brain-to-brain communication. The film also stars Ryan Gosling as Alex Rivera, a hacker who helps Maya navigate the digital consciousness realm, and Lupita Nyong'o as Dr. Zara Okonkwo, the ethics committee chair trying to regulate the technology. Set in a near-future world where brain-computer interfaces have become mainstream, the story follows Maya's journey as she discovers that her neural interface can access collective human memories and must prevent a global consciousness hack that could merge all human minds into a single entity. The film features stunning visual effects, philosophical depth, and explores themes of identity, privacy, and the nature of consciousness. It was shot using revolutionary brain-scanning technology and received critical acclaim for its scientific accuracy and emotional depth.";
        TextSegment segment = TextSegment.from(knowledge, new Metadata().put("source", "movie_docs"));
        Embedding embedding = embeddingModel.embed(segment).content();
        String id = store.add(embedding, segment);

        // User question
        String question = "What is Neural Nexus 2025 about and who are the main characters?";

        // FIRST: Ask without RAG context
        logger.info("=== TESTING CLAUDE WITHOUT RAG CONTEXT ===");
        logger.info("Question: {}", question);
        logger.info("Sending question to Claude without RAG context...");

        long startTime = System.currentTimeMillis();
        String responseWithoutRAG = claude.chat(question);
        long endTime = System.currentTimeMillis();

        logger.info("Claude Response (No RAG) - Time: {}ms", endTime - startTime);
        logger.info("Response: {}", responseWithoutRAG);

        // SECOND: Ask with RAG context
        logger.info("=== TESTING CLAUDE WITH RAG CONTEXT ===");
        logger.info("Generating embeddings for question...");

        // Retrieve context
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        logger.info("Searching for relevant context in vector database...");

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .build());

        // Build prompt with context
        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            context.append(match.embedded().text()).append("\n");
        }

        String prompt =
                String.format("Based on this context:\n%s\n\nAnswer the question: %s", context.toString(), question);

        logger.info("Context retrieved: {}", context.toString());
        logger.info("Sending question with RAG context to Claude...");

        // Get Claude response with RAG
        startTime = System.currentTimeMillis();
        String responseWithRAG = claude.chat(prompt);
        endTime = System.currentTimeMillis();

        // Verify response
        assertThat(responseWithRAG).isNotBlank();
        assertThat(responseWithRAG.toLowerCase())
                .containsAnyOf(
                        "neural", "nexus", "rodriguez", "johansson", "gosling", "nyong'o", "maya", "alex", "zara");

        logger.info("Claude Response (With RAG) - Time: {}ms", endTime - startTime);
        logger.info("Response: {}", responseWithRAG);

        // Compare responses
        logger.info("=== {} CLAUDE COMPARISON ===", logPrefix);
        logger.info("{} Response without RAG: {}", logPrefix, responseWithoutRAG);
        logger.info("{} Response with RAG: {}", logPrefix, responseWithRAG);
        logger.info("✅ {} Claude RAG pipeline test completed successfully", logPrefix);

        // Cleanup
        store.removeAll(List.of(id));
    }

    /**
     * Test Ollama RAG pipeline with the provided store.
     * @param store The embedding store to test
     * @param logPrefix Log prefix for driver identification (e.g., "[POSTGRESQL]" or "[SMART-DRIVER]")
     */
    private static void testOllamaRAGPipeline(YugabyteDBEmbeddingStore store, String logPrefix) {
        ChatModel llm = OllamaChatModel.builder()
                .modelName("tinyllama")
                .baseUrl(ollamaBaseUrl)
                .timeout(Duration.ofMinutes(2))
                .build();

        // Store knowledge base about a fictional 2025 movie
        String knowledge =
                "Quantum Dreams 2025 is a groundbreaking science fiction thriller directed by Alex Chen, starring Emma Watson as Dr. Sarah Chen, a quantum physicist who discovers a way to manipulate reality through quantum entanglement. The film also stars Tom Holland as Marcus Webb, a hacker who helps Sarah navigate the digital realm, and Viola Davis as General Patricia Williams, the military leader trying to control the technology. Set in a near-future world where quantum computing has advanced beyond current capabilities, the story follows Sarah's race against time to prevent a quantum apocalypse that could destroy the fabric of space-time. The film features cutting-edge visual effects, mind-bending plot twists, and explores themes of technology, consciousness, and the nature of reality. It was shot using revolutionary quantum-camera technology and received unprecedented critical acclaim for its scientific accuracy and storytelling innovation.";
        TextSegment segment = TextSegment.from(knowledge, new Metadata().put("source", "movie_docs"));
        Embedding embedding = embeddingModel.embed(segment).content();
        String id = store.add(embedding, segment);

        // User question
        String question = "What is Quantum Dreams 2025 movie about and who are the main characters?";

        // FIRST: Ask without RAG context
        logger.info("=== TESTING WITHOUT RAG CONTEXT ===");
        logger.info("Question: {}", question);
        logger.info("Sending question to Ollama without RAG context...");

        long startTime = System.currentTimeMillis();
        String responseWithoutRAG = llm.chat(question);
        long endTime = System.currentTimeMillis();

        logger.info("Ollama Response (No RAG) - Time: {}ms", endTime - startTime);
        logger.info("Response: {}", responseWithoutRAG);

        // SECOND: Ask with RAG context
        logger.info("=== TESTING WITH RAG CONTEXT ===");
        logger.info("Generating embeddings for question...");

        // Retrieve context
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        logger.info("Searching for relevant context in vector database...");

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .build());

        // Build prompt with context
        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            context.append(match.embedded().text()).append("\n");
        }

        String prompt =
                String.format("Based on this context:\n%s\n\nAnswer the question: %s", context.toString(), question);

        logger.info("Context retrieved: {}", context.toString());
        logger.info("Sending question with RAG context to Ollama...");

        // Get LLM response with RAG
        startTime = System.currentTimeMillis();
        String responseWithRAG = llm.chat(prompt);
        endTime = System.currentTimeMillis();

        // Verify response
        assertThat(responseWithRAG).isNotBlank();
        assertThat(responseWithRAG.toLowerCase())
                .containsAnyOf(
                        "quantum", "dreams", "chen", "watson", "holland", "davis", "sarah", "marcus", "patricia");

        logger.info("Ollama Response (With RAG) - Time: {}ms", endTime - startTime);
        logger.info("Response: {}", responseWithRAG);

        // Compare responses
        logger.info("=== {} OLLAMA COMPARISON ===", logPrefix);
        logger.info("{} Response without RAG: {}", logPrefix, responseWithoutRAG);
        logger.info("{} Response with RAG: {}", logPrefix, responseWithRAG);
        logger.info("✅ {} Ollama RAG pipeline test completed successfully", logPrefix);

        // Cleanup
        store.removeAll(List.of(id));
    }

    /**
     * Test RAG context quality with the provided store.
     * @param store The embedding store to test
     * @param logPrefix Log prefix for driver identification (e.g., "[POSTGRESQL]" or "[SMART-DRIVER]")
     */
    private static void testRAGContextQuality(YugabyteDBEmbeddingStore store, String logPrefix) {
        // Store diverse knowledge base
        List<String> knowledgeItems = List.of(
                "YugabyteDB uses the Raft consensus algorithm for strong consistency across distributed nodes.",
                "The database supports both YSQL (PostgreSQL-compatible) and YCQL (Cassandra-compatible) APIs.",
                "YugabyteDB automatically shards data across nodes and can handle petabyte-scale datasets.",
                "Built-in backup and restore capabilities ensure data durability and disaster recovery.",
                "The architecture separates storage and compute layers for independent scaling.");

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < knowledgeItems.size(); i++) {
            String content = knowledgeItems.get(i);
            Metadata metadata =
                    new Metadata().put("doc_id", "kb_" + i).put("category", i < 2 ? "consistency" : "scalability");

            TextSegment segment = TextSegment.from(content, metadata);
            Embedding embedding = embeddingModel.embed(segment).content();
            String id = store.add(embedding, segment);
            ids.add(id);
        }

        // Test different types of questions
        String[] questions = {
            "How does YugabyteDB ensure data consistency?", "How does YugabyteDB handle large datasets?"
        };

        for (String question : questions) {
            logger.info("\n{} === Testing Question: {} ===", logPrefix, question);

            Embedding queryEmbedding = embeddingModel.embed(question).content();
            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(2)
                    .minScore(0.3)
                    .build());

            assertThat(result.matches()).isNotEmpty();

            logger.info("{} Retrieved Context:", logPrefix);
            for (int i = 0; i < result.matches().size(); i++) {
                EmbeddingMatch<TextSegment> match = result.matches().get(i);
                logger.info(
                        "{} {}. [Score: {}] {}",
                        logPrefix,
                        i + 1,
                        String.format("%.3f", match.score()),
                        match.embedded().text());
            }

            // Verify context relevance
            String combinedContext = result.matches().stream()
                    .map(match -> match.embedded().text().toLowerCase())
                    .reduce("", (a, b) -> a + " " + b);

            if (question.toLowerCase().contains("consistency")) {
                assertThat(combinedContext).containsAnyOf("raft", "consistency", "consensus");
            } else if (question.toLowerCase().contains("dataset")) {
                assertThat(combinedContext).containsAnyOf("shard", "petabyte", "scale");
            }
        }

        logger.info("✅ {} YugabyteDB provides high-quality, relevant context for LLM responses", logPrefix);
        logger.info("✅ {} RAG context quality test completed successfully", logPrefix);

        // Cleanup
        store.removeAll(ids);
    }
}
