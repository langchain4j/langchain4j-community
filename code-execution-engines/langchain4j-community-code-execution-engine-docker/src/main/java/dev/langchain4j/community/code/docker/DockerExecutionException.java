package dev.langchain4j.community.code.docker;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when Docker code execution fails.
 * Provides error type, image, exit code, and stderr when available.
 */
public class DockerExecutionException extends LangChain4jException {

    /** Classification of Docker execution errors. */
    public enum ErrorType {
        DOCKER_NOT_AVAILABLE,
        IMAGE_NOT_FOUND,
        IMAGE_PULL_FAILED,
        CONTAINER_CREATE_FAILED,
        EXECUTION_TIMEOUT,
        EXECUTION_FAILED,
        OUTPUT_LIMIT_EXCEEDED,
        RESOURCE_LIMIT_EXCEEDED,
        CODE_COPY_FAILED,
        UNKNOWN
    }

    private final ErrorType errorType;
    private final String image;
    private final Integer exitCode;
    private final String stderr;

    /** Creates exception with message and cause. */
    public DockerExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.UNKNOWN;
        this.image = null;
        this.exitCode = null;
        this.stderr = null;
    }

    /** Creates exception with error type and message. */
    public DockerExecutionException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.image = null;
        this.exitCode = null;
        this.stderr = null;
    }

    /** Creates exception with error type, message, and image. */
    public DockerExecutionException(ErrorType errorType, String message, String image) {
        super(message);
        this.errorType = errorType;
        this.image = image;
        this.exitCode = null;
        this.stderr = null;
    }

    /** Creates exception with full execution details. */
    public DockerExecutionException(
            ErrorType errorType, String message, String image, Integer exitCode, String stderr) {
        super(message);
        this.errorType = errorType;
        this.image = image;
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    /** Creates exception with error type, message, image, and cause. */
    public DockerExecutionException(ErrorType errorType, String message, String image, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.image = image;
        this.exitCode = null;
        this.stderr = null;
    }

    /** Returns the error type. */
    public ErrorType getErrorType() {
        return errorType;
    }

    /** Returns the Docker image, or null. */
    public String getImage() {
        return image;
    }

    /** Returns exit code, or null. */
    public Integer getExitCode() {
        return exitCode;
    }

    /** Returns stderr, or null. */
    public String getStderr() {
        return stderr;
    }

    /** Creates "Docker not available" exception. */
    public static DockerExecutionException dockerNotAvailable(Throwable cause) {
        return new DockerExecutionException(
                ErrorType.DOCKER_NOT_AVAILABLE,
                "Docker daemon is not available. Please ensure Docker is installed and running.",
                null,
                cause);
    }

    /** Creates "image not found" exception. */
    public static DockerExecutionException imageNotFound(String image) {
        return new DockerExecutionException(ErrorType.IMAGE_NOT_FOUND, "Docker image not found: " + image, image);
    }

    /** Creates "image pull failed" exception. */
    public static DockerExecutionException imagePullFailed(String image, Throwable cause) {
        return new DockerExecutionException(
                ErrorType.IMAGE_PULL_FAILED, "Failed to pull Docker image: " + image, image, cause);
    }

    /** Creates "container create failed" exception. */
    public static DockerExecutionException containerCreateFailed(String image, Throwable cause) {
        return new DockerExecutionException(
                ErrorType.CONTAINER_CREATE_FAILED, "Failed to create container with image: " + image, image, cause);
    }

    /** Creates "execution timeout" exception. */
    public static DockerExecutionException executionTimeout(String image, long timeout) {
        return new DockerExecutionException(
                ErrorType.EXECUTION_TIMEOUT, "Code execution timed out after " + timeout + " seconds", image);
    }

    /** Creates "execution failed" exception. */
    public static DockerExecutionException executionFailed(String image, int exitCode, String stderr) {
        return new DockerExecutionException(
                ErrorType.EXECUTION_FAILED,
                "Code execution failed with exit code " + exitCode,
                image,
                exitCode,
                stderr);
    }

    /** Creates "code copy failed" exception. */
    public static DockerExecutionException codeCopyFailed(Throwable cause) {
        return new DockerExecutionException(
                ErrorType.CODE_COPY_FAILED, "Failed to copy code to container", null, cause);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DockerExecutionException{");
        sb.append("errorType=").append(errorType);
        if (image != null) {
            sb.append(", image='").append(image).append('\'');
        }
        if (exitCode != null) {
            sb.append(", exitCode=").append(exitCode);
        }
        if (stderr != null && !stderr.isEmpty()) {
            sb.append(", stderr='").append(truncate(stderr, 100)).append('\'');
        }
        sb.append(", message='").append(getMessage()).append('\'');
        sb.append('}');
        return sb.toString();
    }

    private static String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength) + "...";
    }
}
