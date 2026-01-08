package dev.langchain4j.community.code.docker;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when Docker code execution fails.
 *
 * <p>Provides error classification via {@link ErrorType}, along with the Docker image,
 * exit code, and stderr output when available.
 *
 * @see DockerCodeExecutionEngine
 */
public class DockerExecutionException extends LangChain4jException {

    /** Classification of Docker execution errors. */
    public enum ErrorType {
        /** Docker daemon is not running or not accessible. */
        DOCKER_NOT_AVAILABLE,
        /** The specified Docker image was not found. */
        IMAGE_NOT_FOUND,
        /** Failed to pull the Docker image from a registry. */
        IMAGE_PULL_FAILED,
        /** Failed to create a Docker container. */
        CONTAINER_CREATE_FAILED,
        /** Code execution exceeded the configured timeout. */
        EXECUTION_TIMEOUT,
        /** Code execution returned a non-zero exit code. */
        EXECUTION_FAILED,
        /** Output exceeded the configured size limit. */
        OUTPUT_LIMIT_EXCEEDED,
        /** Container exceeded memory or CPU limits. */
        RESOURCE_LIMIT_EXCEEDED,
        /** Failed to copy code into the container. */
        CODE_COPY_FAILED,
        /** Unclassified error. */
        UNKNOWN
    }

    private final ErrorType errorType;
    private final String image;
    private final Integer exitCode;
    private final String stderr;

    /**
     * Creates an exception with message and cause.
     *
     * <p>The error type defaults to {@link ErrorType#UNKNOWN} and no image,
     * exit code, or stderr information is captured.
     *
     * @param message the detail message describing the error
     * @param cause the underlying cause of the exception
     */
    public DockerExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.UNKNOWN;
        this.image = null;
        this.exitCode = null;
        this.stderr = null;
    }

    /**
     * Creates an exception with a specific error type and message.
     *
     * @param errorType the classification of the error
     * @param message the detail message describing the error
     */
    public DockerExecutionException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.image = null;
        this.exitCode = null;
        this.stderr = null;
    }

    /**
     * Creates an exception with error type, message, and Docker image name.
     *
     * @param errorType the classification of the error
     * @param message the detail message describing the error
     * @param image the Docker image involved (e.g., "python:3.12-slim")
     */
    public DockerExecutionException(ErrorType errorType, String message, String image) {
        super(message);
        this.errorType = errorType;
        this.image = image;
        this.exitCode = null;
        this.stderr = null;
    }

    /**
     * Creates an exception with full execution details including exit code and stderr.
     *
     * <p>Use this constructor for execution failures where the code ran but
     * returned a non-zero exit code.
     *
     * @param errorType the classification of the error
     * @param message the detail message describing the error
     * @param image the Docker image used for execution
     * @param exitCode the exit code returned by the container
     * @param stderr the standard error output from the execution
     */
    public DockerExecutionException(
            ErrorType errorType, String message, String image, Integer exitCode, String stderr) {
        super(message);
        this.errorType = errorType;
        this.image = image;
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    /**
     * Creates an exception with error type, message, Docker image, and underlying cause.
     *
     * @param errorType the classification of the error
     * @param message the detail message describing the error
     * @param image the Docker image involved in the error
     * @param cause the underlying cause of the exception
     */
    public DockerExecutionException(ErrorType errorType, String message, String image, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.image = image;
        this.exitCode = null;
        this.stderr = null;
    }

    /**
     * Returns the error type classification.
     *
     * @return the error type, never null
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns the Docker image involved in the error.
     *
     * @return the Docker image name, or null if not applicable
     */
    public String getImage() {
        return image;
    }

    /**
     * Returns the container's exit code.
     *
     * <p>Common exit codes: 0=success, 1=general error, 137=killed (OOM), 139=segfault.
     *
     * @return the exit code, or null if execution did not complete
     */
    public Integer getExitCode() {
        return exitCode;
    }

    /**
     * Returns the standard error output from the execution.
     *
     * @return the stderr output, or null if not available
     */
    public String getStderr() {
        return stderr;
    }

    /**
     * Creates an exception indicating that Docker daemon is not available.
     *
     * @param cause the underlying cause (e.g., connection refused)
     * @return a new exception with type {@link ErrorType#DOCKER_NOT_AVAILABLE}
     */
    public static DockerExecutionException dockerNotAvailable(Throwable cause) {
        return new DockerExecutionException(
                ErrorType.DOCKER_NOT_AVAILABLE,
                "Docker daemon is not available. Please ensure Docker is installed and running.",
                null,
                cause);
    }

    /**
     * Creates an exception indicating that a Docker image was not found.
     *
     * @param image the Docker image that was not found
     * @return a new exception with type {@link ErrorType#IMAGE_NOT_FOUND}
     */
    public static DockerExecutionException imageNotFound(String image) {
        return new DockerExecutionException(ErrorType.IMAGE_NOT_FOUND, "Docker image not found: " + image, image);
    }

    /**
     * Creates an exception indicating that pulling a Docker image failed.
     *
     * @param image the Docker image that could not be pulled
     * @param cause the underlying cause of the pull failure
     * @return a new exception with type {@link ErrorType#IMAGE_PULL_FAILED}
     */
    public static DockerExecutionException imagePullFailed(String image, Throwable cause) {
        return new DockerExecutionException(
                ErrorType.IMAGE_PULL_FAILED, "Failed to pull Docker image: " + image, image, cause);
    }

    /**
     * Creates an exception indicating that container creation failed.
     *
     * @param image the Docker image from which container creation was attempted
     * @param cause the underlying cause of the creation failure
     * @return a new exception with type {@link ErrorType#CONTAINER_CREATE_FAILED}
     */
    public static DockerExecutionException containerCreateFailed(String image, Throwable cause) {
        return new DockerExecutionException(
                ErrorType.CONTAINER_CREATE_FAILED, "Failed to create container with image: " + image, image, cause);
    }

    /**
     * Creates an exception indicating that code execution timed out.
     *
     * @param image the Docker image used for execution
     * @param timeout the timeout duration in seconds that was exceeded
     * @return a new exception with type {@link ErrorType#EXECUTION_TIMEOUT}
     */
    public static DockerExecutionException executionTimeout(String image, long timeout) {
        return new DockerExecutionException(
                ErrorType.EXECUTION_TIMEOUT, "Code execution timed out after " + timeout + " seconds", image);
    }

    /**
     * Creates an exception indicating that code execution failed.
     *
     * @param image the Docker image used for execution
     * @param exitCode the non-zero exit code returned by the container
     * @param stderr the standard error output containing error details
     * @return a new exception with type {@link ErrorType#EXECUTION_FAILED}
     */
    public static DockerExecutionException executionFailed(String image, int exitCode, String stderr) {
        return new DockerExecutionException(
                ErrorType.EXECUTION_FAILED,
                "Code execution failed with exit code " + exitCode,
                image,
                exitCode,
                stderr);
    }

    /**
     * Creates an exception indicating that copying code to the container failed.
     *
     * @param cause the underlying cause of the copy failure
     * @return a new exception with type {@link ErrorType#CODE_COPY_FAILED}
     */
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
