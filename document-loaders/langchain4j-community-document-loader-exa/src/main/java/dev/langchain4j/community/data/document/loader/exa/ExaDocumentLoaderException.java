package dev.langchain4j.community.data.document.loader.exa;

/**
 * Runtime exception thrown by {@link ExaDocumentLoader} to indicate errors
 * encountered during search, API request, or response processing with the Exa
 * search API.
 *
 * <p>
 * This exception wraps various failure scenarios, including but not limited to:</p>
 * <ul>
 * <li>HTTP request failures (e.g., network errors, non-success status
 * codes)</li>
 * <li>Invalid or malformed API responses</li>
 * <li>JSON parsing errors while deserializing Exa API results</li>
 * <li>Unexpected runtime issues during document extraction or metadata
 * mapping</li>
 * </ul>
 *
 *
 * <p>
 * All {@link ExaDocumentLoader} methods that interact with the Exa API
 * internally catch lower-level exceptions and rethrow them as
 * {@code ExaDocumentLoaderException} to provide a consistent exception model
 * for callers.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * try {
 * 	ExaDocumentLoader loader = ExaDocumentLoader.builder().apiKey("your-api-key").numResults(5).build();
 *
 * 	List<Document> documents = loader.loadDocuments("latest AI research");
 * } catch (ExaDocumentLoaderException e) {
 * 	System.err.println("Failed to load documents: " + e.getMessage());
 * 	e.printStackTrace();
 * }
 * }</pre>
 *
 * @see ExaDocumentLoader
 */
public class ExaDocumentLoaderException extends RuntimeException {

    /**
     * Constructs a new {@code ExaDocumentLoaderException} with the specified detail
     * message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public ExaDocumentLoaderException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ExaDocumentLoaderException} with the specified detail
     * message and cause.
     *
     * <p>
     * The {@code cause} can be any underlying exception, such as
     * {@link java.io.IOException}, {@link dev.langchain4j.exception.HttpException}, or
     * runtime exceptions encountered during processing.
     * </p>
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause   the underlying exception that triggered this exception
     */
    public ExaDocumentLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
