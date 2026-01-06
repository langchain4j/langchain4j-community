package dev.langchain4j.community.code.docker;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for {@link DockerCodeExecutionTool} with a real LLM.
 *
 * <p>These tests verify that the Docker code execution tool integrates correctly
 * with LangChain4j AI services and can be invoked by an LLM to execute code
 * in isolated Docker containers.
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>Docker daemon must be running</li>
 *   <li>OPENAI_API_KEY environment variable must be set</li>
 *   <li>Optional: OPENAI_BASE_URL and OPENAI_ORGANIZATION_ID for custom setups</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class DockerCodeExecutionToolIT {

    private static final Logger logger = LoggerFactory.getLogger(DockerCodeExecutionToolIT.class);

    private OpenAiChatModel model;
    private DockerCodeExecutionTool tool;

    @BeforeAll
    static void checkDockerAvailable() {
        DockerTestUtils.assumeDockerAvailable();
    }

    @BeforeEach
    void setUp() {
        model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        tool = new DockerCodeExecutionTool();
    }

    @AfterEach
    void tearDown() {
        if (tool != null) {
            tool.getEngine().close();
        }
    }

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_execute_python_code_via_llm() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.chat("Calculate the factorial of 10 using Python and tell me the result");
        logger.info("LLM response: {}", answer);

        assertThat(answer).isNotEmpty();
        // LLM may format with or without commas: "3628800" or "3,628,800"
        assertThat(answer).containsAnyOf("3628800", "3,628,800");
    }

    @Test
    void should_execute_javascript_code_via_llm() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer =
                assistant.chat("Use JavaScript to calculate the sum of numbers from 1 to 100 and tell me the result");
        logger.info("LLM response: {}", answer);

        assertThat(answer).isNotEmpty();
        assertThat(answer).containsIgnoringCase("5050");
    }

    @Test
    void should_execute_multi_step_python_code_via_llm() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.chat(
                "Write and execute Python code that generates the first 10 Fibonacci numbers and prints them as a comma-separated list");
        logger.info("LLM response: {}", answer);

        assertThat(answer).isNotEmpty();
        // Should contain Fibonacci numbers: 0, 1, 1, 2, 3, 5, 8, 13, 21, 34
        assertThat(answer).containsAnyOf("0", "1", "2", "3", "5", "8", "13", "21", "34");
    }

    @Test
    void should_handle_code_with_error_gracefully() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.chat(
                "Execute Python code that divides 10 by 0. Tell me what happened and what error occurred.");
        logger.info("LLM response: {}", answer);

        assertThat(answer).isNotEmpty();
        // The LLM should report on the error
        assertThat(answer.toLowerCase()).containsAnyOf("error", "exception", "zero", "failed", "cannot");
    }
}
