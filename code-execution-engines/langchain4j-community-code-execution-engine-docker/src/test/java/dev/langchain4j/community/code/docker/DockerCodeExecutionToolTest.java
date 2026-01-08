package dev.langchain4j.community.code.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DockerCodeExecutionTool}.
 * Tests annotation configuration, error handling, and engine delegation.
 */
class DockerCodeExecutionToolTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_throw_when_engine_is_null() {
        assertThatThrownBy(() -> new DockerCodeExecutionTool(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void should_create_with_custom_engine() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        assertThat(tool).isNotNull();
        assertThat(tool.getEngine()).isSameAs(mockEngine);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOL ANNOTATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void execute_method_should_have_tool_annotation() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        Tool toolAnnotation = executeMethod.getAnnotation(Tool.class);

        assertThat(toolAnnotation).isNotNull();
        // @Tool.value() returns String[] - get first element for substring matching
        String toolDescription = String.join(" ", toolAnnotation.value());
        assertThat(toolDescription)
                .contains("Execute code")
                .contains("Docker container")
                .contains("python:3.12-slim")
                .contains("node:20-alpine")
                .contains("timeoutSeconds");
    }

    @Test
    void execute_method_parameters_should_have_p_annotations() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        Parameter[] parameters = executeMethod.getParameters();

        assertThat(parameters).hasSize(5);

        // All parameters should have @P annotation
        for (Parameter param : parameters) {
            P pAnnotation = param.getAnnotation(P.class);
            assertThat(pAnnotation).isNotNull();
            assertThat(pAnnotation.value()).isNotBlank();
        }
    }

    @Test
    void image_parameter_should_have_descriptive_annotation() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        P pAnnotation = executeMethod.getParameters()[0].getAnnotation(P.class);

        assertThat(pAnnotation.value()).contains("Docker image").contains("python:3.12-slim");
    }

    @Test
    void fileExtension_parameter_should_have_descriptive_annotation() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        P pAnnotation = executeMethod.getParameters()[1].getAnnotation(P.class);

        assertThat(pAnnotation.value())
                .contains("File extension")
                .contains(".py")
                .contains(".js");
    }

    @Test
    void code_parameter_should_have_descriptive_annotation() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        P pAnnotation = executeMethod.getParameters()[2].getAnnotation(P.class);

        assertThat(pAnnotation.value()).contains("source code");
    }

    @Test
    void command_parameter_should_have_descriptive_annotation() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        P pAnnotation = executeMethod.getParameters()[3].getAnnotation(P.class);

        assertThat(pAnnotation.value()).contains("Command").contains("python").contains("node");
    }

    @Test
    void timeout_parameter_should_have_descriptive_annotation() throws NoSuchMethodException {
        Method executeMethod = DockerCodeExecutionTool.class.getMethod(
                "execute", String.class, String.class, String.class, String.class, Integer.class);

        P pAnnotation = executeMethod.getParameters()[4].getAnnotation(P.class);

        assertThat(pAnnotation.value()).contains("Timeout").contains("seconds");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXECUTION TESTS (with mock engine)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_delegate_execution_to_engine_with_default_timeout() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn("Hello, World!");

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('Hello, World!')", "python", null);

        assertThat(result).isEqualTo("Hello, World!");
        verify(mockEngine)
                .execute("python:3.12-slim", ".py", "print('Hello, World!')", "python", Duration.ofSeconds(30));
    }

    @Test
    void should_delegate_execution_to_engine_with_custom_timeout() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn("Hello, World!");

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('Hello, World!')", "python", 60);

        assertThat(result).isEqualTo("Hello, World!");
        verify(mockEngine)
                .execute("python:3.12-slim", ".py", "print('Hello, World!')", "python", Duration.ofSeconds(60));
    }

    @Test
    void should_return_success_message_for_empty_output() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn("");

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "x = 1", "python", null);

        assertThat(result).isEqualTo("Execution completed successfully (no output)");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_format_docker_not_available_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.dockerNotAvailable(new RuntimeException("Connection refused")));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('test')", "python", null);

        assertThat(result).contains("Docker is not available").contains("Docker daemon");
    }

    @Test
    void should_format_image_not_found_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.imageNotFound("nonexistent:latest"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("nonexistent:latest", ".py", "print('test')", "python", null);

        assertThat(result).contains("nonexistent:latest").contains("not found");
    }

    @Test
    void should_format_image_pull_failed_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.imagePullFailed(
                        "python:3.12-slim", new RuntimeException("Network error")));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('test')", "python", null);

        assertThat(result).contains("Failed to pull image").contains("python:3.12-slim");
    }

    @Test
    void should_format_container_create_failed_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.containerCreateFailed(
                        "python:3.12-slim", new RuntimeException("Resource limit")));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('test')", "python", null);

        assertThat(result).contains("Failed to create container").contains("python:3.12-slim");
    }

    @Test
    void should_format_execution_timeout_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.executionTimeout("python:3.12-slim", 30));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "while True: pass", "python", null);

        assertThat(result).contains("timed out").contains("too long");
    }

    @Test
    void should_format_execution_failed_error_with_stderr() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(
                        DockerExecutionException.executionFailed(
                                "python:3.12-slim",
                                1,
                                "Traceback (most recent call last):\n  File \"code.py\", line 1\n    print(undefined)\nNameError: name 'undefined' is not defined"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print(undefined)", "python", null);

        assertThat(result).contains("Execution failed").contains("exit code 1").contains("NameError");
    }

    @Test
    void should_format_execution_failed_error_without_stderr() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DockerExecutionException(
                        DockerExecutionException.ErrorType.EXECUTION_FAILED,
                        "Execution failed",
                        "python:3.12-slim",
                        127,
                        null));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "nonexistent_command", "python", null);

        assertThat(result).contains("exit code 127");
    }

    @Test
    void should_format_output_limit_exceeded_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DockerExecutionException(
                        DockerExecutionException.ErrorType.OUTPUT_LIMIT_EXCEEDED, "Output limit exceeded"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('x' * 10000000)", "python", null);

        assertThat(result).contains("Output limit exceeded");
    }

    @Test
    void should_format_resource_limit_exceeded_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DockerExecutionException(
                        DockerExecutionException.ErrorType.RESOURCE_LIMIT_EXCEEDED, "Memory limit exceeded"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "x = [0] * 10**9", "python", null);

        assertThat(result).contains("Resource limit exceeded").contains("memory");
    }

    @Test
    void should_format_code_copy_failed_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.codeCopyFailed(new RuntimeException("I/O error")));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('test')", "python", null);

        assertThat(result).contains("Failed to copy code");
    }

    @Test
    void should_format_unknown_error() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DockerExecutionException(
                        DockerExecutionException.ErrorType.UNKNOWN, "Something unexpected happened"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('test')", "python", null);

        assertThat(result).contains("Execution error").contains("Something unexpected happened");
    }

    @Test
    void should_handle_invalid_arguments() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalArgumentException("image cannot be blank"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("", ".py", "print('test')", "python", null);

        assertThat(result).contains("Invalid arguments").contains("image cannot be blank");
    }

    @Test
    void should_handle_unexpected_exception() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Completely unexpected"));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print('test')", "python", null);

        assertThat(result).contains("Unexpected error").contains("Completely unexpected");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STDERR TRUNCATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_truncate_long_stderr() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        String longStderr = "Error: " + "x".repeat(1000);
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.executionFailed("python:3.12-slim", 1, longStderr));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "invalid", "python", null);

        assertThat(result).contains("... (truncated)").hasSizeLessThan(longStderr.length());
    }

    @Test
    void should_not_truncate_short_stderr() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        DockerExecutionConfig mockConfig = mock(DockerExecutionConfig.class);
        when(mockConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(mockEngine.getConfig()).thenReturn(mockConfig);
        String shortStderr = "NameError: name 'x' is not defined";
        when(mockEngine.execute(anyString(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenThrow(DockerExecutionException.executionFailed("python:3.12-slim", 1, shortStderr));

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        String result = tool.execute("python:3.12-slim", ".py", "print(x)", "python", null);

        assertThat(result).contains(shortStderr).doesNotContain("truncated");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AVAILABILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_delegate_availability_check_to_engine() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        when(mockEngine.isAvailable()).thenReturn(true);

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        assertThat(tool.isAvailable()).isTrue();
        verify(mockEngine).isAvailable();
    }

    @Test
    void should_return_false_when_docker_unavailable() {
        DockerCodeExecutionEngine mockEngine = mock(DockerCodeExecutionEngine.class);
        when(mockEngine.isAvailable()).thenReturn(false);

        DockerCodeExecutionTool tool = new DockerCodeExecutionTool(mockEngine);

        assertThat(tool.isAvailable()).isFalse();
    }
}
