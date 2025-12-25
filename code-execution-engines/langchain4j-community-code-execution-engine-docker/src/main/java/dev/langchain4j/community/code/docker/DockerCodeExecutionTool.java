package dev.langchain4j.community.code.docker;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * AI agent tool for executing code in isolated Docker containers.
 * LLM provides runtime parameters (image, code, command) while engine enforces security.
 */
public class DockerCodeExecutionTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerCodeExecutionTool.class);

    private final DockerCodeExecutionEngine engine;

    /** Creates a new tool with secure default engine configuration. */
    public DockerCodeExecutionTool() {
        this(DockerCodeExecutionEngine.withDefaultConfig());
    }

    /** Creates a new tool with a custom engine configuration. */
    public DockerCodeExecutionTool(DockerCodeExecutionEngine engine) {
        this.engine = ensureNotNull(engine, "engine");
    }

    /**
     * Executes code in an isolated Docker container.
     * Returns stdout on success or a formatted error message on failure.
     */
    @Tool("Execute code in an isolated Docker container. " +
          "You MUST specify the Docker image, file extension, and command. " +
          "Common images: python:3.12-slim, node:20-alpine, ruby:3.3-slim, " +
          "golang:1.22-alpine, rust:1.75-slim, openjdk:21-slim. " +
          "The code runs with network disabled for security. " +
          "Returns stdout of execution or error message.")
    public String execute(
            @P("Docker image to use (e.g., 'python:3.12-slim', 'node:20-alpine', 'golang:1.22-alpine')")
            String image,

            @P("File extension for the code file (e.g., '.py', '.js', '.go', '.rs', '.java', '.rb', '.sh')")
            String fileExtension,

            @P("The source code to execute")
            String code,

            @P("Command to run the code (e.g., 'python' for Python, 'node' for JavaScript, " +
               "'ruby' for Ruby, 'go run' for Go, 'sh' for shell scripts)")
            String command
    ) {
        LOGGER.debug("Tool executing code with image: {}, extension: {}, command: {}",
                image, fileExtension, command);

        try {
            String result = engine.execute(image, fileExtension, code, command);
            LOGGER.debug("Tool execution completed successfully");
            return result.isEmpty() ? "Execution completed successfully (no output)" : result;
        } catch (DockerExecutionException e) {
            LOGGER.warn("Tool execution failed: {}", e.getMessage());
            return formatError(e);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Tool received invalid arguments: {}", e.getMessage());
            return "Invalid arguments: " + e.getMessage();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during tool execution", e);
            return "Unexpected error: " + e.getMessage();
        }
    }

    /** Formats a DockerExecutionException into an LLM-friendly error message. */
    private String formatError(DockerExecutionException e) {
        return switch (e.getErrorType()) {
            case DOCKER_NOT_AVAILABLE ->
                    "Docker is not available. Cannot execute code. " +
                    "Please ensure Docker daemon is running.";

            case IMAGE_NOT_FOUND ->
                    "Image '" + e.getImage() + "' not found. " +
                    "Try a different image or check the image name.";

            case IMAGE_PULL_FAILED ->
                    "Failed to pull image '" + e.getImage() + "'. " +
                    "Check network connectivity or try a different image.";

            case CONTAINER_CREATE_FAILED ->
                    "Failed to create container with image '" + e.getImage() + "'. " +
                    "The image may not be compatible or Docker resources are exhausted.";

            case EXECUTION_TIMEOUT ->
                    "Execution timed out. The code took too long to run. " +
                    "Simplify the code or check for infinite loops.";

            case EXECUTION_FAILED -> {
                String stderr = e.getStderr();
                if (stderr != null && !stderr.isEmpty()) {
                    yield "Execution failed (exit code " + e.getExitCode() + "):\n" + truncateStderr(stderr);
                } else {
                    yield "Execution failed with exit code " + e.getExitCode() + ".";
                }
            }

            case OUTPUT_LIMIT_EXCEEDED ->
                    "Output limit exceeded. The code produced too much output.";

            case RESOURCE_LIMIT_EXCEEDED ->
                    "Resource limit exceeded (memory or CPU). " +
                    "Reduce memory usage or simplify the computation.";

            case CODE_COPY_FAILED ->
                    "Failed to copy code to container. " +
                    "This is an internal error.";

            case UNKNOWN ->
                    "Execution error: " + e.getMessage();
        };
    }

    /** Truncates stderr to a reasonable length for LLM consumption. */
    private String truncateStderr(String stderr) {
        int maxLength = 500;
        if (stderr.length() <= maxLength) {
            return stderr;
        }
        return stderr.substring(0, maxLength) + "\n... (truncated)";
    }

    /** Checks if Docker is available. */
    public boolean isAvailable() {
        return engine.isAvailable();
    }

    /** Returns the underlying execution engine. */
    public DockerCodeExecutionEngine getEngine() {
        return engine;
    }
}
