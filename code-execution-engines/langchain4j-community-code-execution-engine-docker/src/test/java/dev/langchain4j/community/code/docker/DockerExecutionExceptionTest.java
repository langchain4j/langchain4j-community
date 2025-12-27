package dev.langchain4j.community.code.docker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.code.docker.DockerExecutionException.ErrorType;
import dev.langchain4j.exception.LangChain4jException;
import org.junit.jupiter.api.Test;

class DockerExecutionExceptionTest {

    @Test
    void should_extend_langchain4j_exception() {
        DockerExecutionException exception = new DockerExecutionException(ErrorType.UNKNOWN, "test message");

        assertThat(exception).isInstanceOf(LangChain4jException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_create_with_message_and_cause() {
        RuntimeException cause = new RuntimeException("root cause");
        DockerExecutionException exception = new DockerExecutionException("test message", cause);

        assertThat(exception.getMessage()).isEqualTo("test message");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNKNOWN);
        assertThat(exception.getImage()).isNull();
        assertThat(exception.getExitCode()).isNull();
        assertThat(exception.getStderr()).isNull();
    }

    @Test
    void should_create_with_error_type_and_message() {
        DockerExecutionException exception =
                new DockerExecutionException(ErrorType.DOCKER_NOT_AVAILABLE, "Docker is not running");

        assertThat(exception.getMessage()).isEqualTo("Docker is not running");
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.DOCKER_NOT_AVAILABLE);
        assertThat(exception.getImage()).isNull();
    }

    @Test
    void should_create_with_error_type_message_and_image() {
        DockerExecutionException exception =
                new DockerExecutionException(ErrorType.IMAGE_NOT_FOUND, "Image not found", "python:3.12-slim");

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.IMAGE_NOT_FOUND);
        assertThat(exception.getImage()).isEqualTo("python:3.12-slim");
    }

    @Test
    void should_create_with_full_execution_details() {
        DockerExecutionException exception = new DockerExecutionException(
                ErrorType.EXECUTION_FAILED, "Execution failed", "python:3.12-slim", 1, "Traceback: ZeroDivisionError");

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_FAILED);
        assertThat(exception.getImage()).isEqualTo("python:3.12-slim");
        assertThat(exception.getExitCode()).isEqualTo(1);
        assertThat(exception.getStderr()).isEqualTo("Traceback: ZeroDivisionError");
    }

    @Test
    void should_create_docker_not_available_exception() {
        RuntimeException cause = new RuntimeException("Connection refused");
        DockerExecutionException exception = DockerExecutionException.dockerNotAvailable(cause);

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.DOCKER_NOT_AVAILABLE);
        assertThat(exception.getMessage()).contains("Docker daemon is not available");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void should_create_image_not_found_exception() {
        DockerExecutionException exception = DockerExecutionException.imageNotFound("nonexistent:latest");

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.IMAGE_NOT_FOUND);
        assertThat(exception.getMessage()).contains("nonexistent:latest");
        assertThat(exception.getImage()).isEqualTo("nonexistent:latest");
    }

    @Test
    void should_create_image_pull_failed_exception() {
        RuntimeException cause = new RuntimeException("Network error");
        DockerExecutionException exception = DockerExecutionException.imagePullFailed("python:3.12-slim", cause);

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.IMAGE_PULL_FAILED);
        assertThat(exception.getMessage()).contains("python:3.12-slim");
        assertThat(exception.getImage()).isEqualTo("python:3.12-slim");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void should_create_container_create_failed_exception() {
        RuntimeException cause = new RuntimeException("Resource limit");
        DockerExecutionException exception = DockerExecutionException.containerCreateFailed("node:20-alpine", cause);

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONTAINER_CREATE_FAILED);
        assertThat(exception.getMessage()).contains("node:20-alpine");
        assertThat(exception.getImage()).isEqualTo("node:20-alpine");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void should_create_execution_timeout_exception() {
        DockerExecutionException exception = DockerExecutionException.executionTimeout("python:3.12-slim", 30);

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_TIMEOUT);
        assertThat(exception.getMessage()).contains("30");
        assertThat(exception.getMessage()).contains("timed out");
        assertThat(exception.getImage()).isEqualTo("python:3.12-slim");
    }

    @Test
    void should_create_execution_failed_exception() {
        DockerExecutionException exception = DockerExecutionException.executionFailed(
                "python:3.12-slim", 1, "NameError: name 'undefined' is not defined");

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_FAILED);
        assertThat(exception.getMessage()).contains("exit code 1");
        assertThat(exception.getImage()).isEqualTo("python:3.12-slim");
        assertThat(exception.getExitCode()).isEqualTo(1);
        assertThat(exception.getStderr()).contains("NameError");
    }

    @Test
    void should_create_code_copy_failed_exception() {
        RuntimeException cause = new RuntimeException("I/O error");
        DockerExecutionException exception = DockerExecutionException.codeCopyFailed(cause);

        assertThat(exception.getErrorType()).isEqualTo(ErrorType.CODE_COPY_FAILED);
        assertThat(exception.getMessage()).contains("copy code");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void should_have_meaningful_to_string() {
        DockerExecutionException exception = new DockerExecutionException(
                ErrorType.EXECUTION_FAILED, "Test failure", "python:3.12-slim", 127, "command not found");

        String toString = exception.toString();

        assertThat(toString)
                .contains("EXECUTION_FAILED")
                .contains("python:3.12-slim")
                .contains("exitCode=127")
                .contains("command not found")
                .contains("Test failure");
    }

    @Test
    void should_truncate_long_stderr_in_to_string() {
        String longStderr = "x".repeat(200);
        DockerExecutionException exception = new DockerExecutionException(
                ErrorType.EXECUTION_FAILED, "Test failure", "python:3.12-slim", 1, longStderr);

        String toString = exception.toString();

        // Should truncate to 100 characters + "..."
        assertThat(toString).contains("x".repeat(100) + "...");
        assertThat(toString).doesNotContain("x".repeat(101));
    }

    @Test
    void should_handle_null_values_in_to_string() {
        DockerExecutionException exception = new DockerExecutionException(ErrorType.UNKNOWN, "Simple error");

        String toString = exception.toString();

        assertThat(toString)
                .contains("UNKNOWN")
                .contains("Simple error")
                .doesNotContain("image=")
                .doesNotContain("exitCode=")
                .doesNotContain("stderr=");
    }

    @Test
    void should_have_all_error_types() {
        // Verify all expected error types exist
        assertThat(ErrorType.values())
                .containsExactlyInAnyOrder(
                        ErrorType.DOCKER_NOT_AVAILABLE,
                        ErrorType.IMAGE_NOT_FOUND,
                        ErrorType.IMAGE_PULL_FAILED,
                        ErrorType.CONTAINER_CREATE_FAILED,
                        ErrorType.EXECUTION_TIMEOUT,
                        ErrorType.EXECUTION_FAILED,
                        ErrorType.OUTPUT_LIMIT_EXCEEDED,
                        ErrorType.RESOURCE_LIMIT_EXCEEDED,
                        ErrorType.CODE_COPY_FAILED,
                        ErrorType.UNKNOWN);
    }
}
