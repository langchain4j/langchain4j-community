package dev.langchain4j.community.model;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.tokenize.CohereTokenizeRequest;
import dev.langchain4j.community.model.client.tokenize.CohereTokenizeResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;

/**
 * Estimates how many tokens a given text or set of chat messages may consume
 * for a given Cohere model.
 * <p>
 * This implementation is based on the <b>/tokenize</b> endpoint of the public API.
 *
 * @see <a href="https://docs.cohere.com/reference/tokenize">Tokenization endpoint specification</a>
 */
public class CohereTokenCountEstimator implements TokenCountEstimator {

    private final String modelName;
    private final CohereClient client;
    private final int maxRetries;

    private CohereTokenCountEstimator(CohereTokenCountEstimatorBuilder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "model name");

        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.cohere.com/v1"))
                .authToken(builder.apiKey)
                .timeout(builder.timeout)
                .logger(builder.logger)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();

        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    @Override
    public int estimateTokenCountInText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }

        if (text.isEmpty()) {
            return 0;
        }

        CohereTokenizeRequest request = CohereTokenizeRequest.from(text, modelName);
        CohereTokenizeResponse response = withRetryMappingExceptions(() -> client.tokenize(request), maxRetries);

        return response.getTokens().size();
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }

        return switch (message.type()) {
            case SYSTEM -> {
                String text = ((SystemMessage) message).text();
                yield estimateTokenCountInText(text);
            }

            case USER -> {
                UserMessage userMessage = (UserMessage) message;
                List<String> parts = new ArrayList<>();

                for (Content content : userMessage.contents()) {
                    if (Objects.requireNonNull(content.type()) == ContentType.TEXT) {
                        String text = ((TextContent) content).text();
                        parts.add(text);
                    } else {
                        throw new IllegalArgumentException("Content type not supported: " + content.type());
                    }
                }

                String text = String.join("\n", parts);
                yield estimateTokenCountInText(text);
            }

            case AI -> {
                AiMessage aiMessage = (AiMessage) message;
                List<String> parts = new ArrayList<>();

                if (isNotNullOrEmpty(aiMessage.text())) {
                    parts.add(aiMessage.text());
                }

                if (isNotNullOrEmpty(aiMessage.thinking())) {
                    parts.add(aiMessage.thinking());
                }

                if (aiMessage.hasToolExecutionRequests()) {
                    aiMessage.toolExecutionRequests().forEach(toolExecutionRequest -> {
                        parts.add(toolExecutionRequest.id());
                        parts.add(toolExecutionRequest.name());
                        parts.add(toolExecutionRequest.arguments());
                    });
                }

                String text = String.join("\n", parts);
                yield estimateTokenCountInText(text);
            }

            case TOOL_EXECUTION_RESULT -> {
                String text = ((ToolExecutionResultMessage) message).text();
                yield estimateTokenCountInText(text);
            }

            default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
        };
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (messages == null) {
            throw new IllegalArgumentException("messages cannot be null");
        }

        return StreamSupport.stream(messages.spliterator(), false)
                .mapToInt(this::estimateTokenCountInMessage)
                .sum();
    }

    public static CohereTokenCountEstimatorBuilder builder() {
        return new CohereTokenCountEstimatorBuilder();
    }

    public static class CohereTokenCountEstimatorBuilder {

        private String modelName;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Logger logger;
        private Boolean logRequests;
        private Boolean logResponses;
        private Integer maxRetries;

        /**
         *  Models might differ in their tokenizer, so this can affect the
         *  token count even for the same text.
         */
        public CohereTokenCountEstimatorBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * The base URL of the Cohere API.
         */
        public CohereTokenCountEstimatorBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The Cohere API key.
         */
        public CohereTokenCountEstimatorBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The timeout value for the request to the {@code /tokenize} endpoint.
         */
        public CohereTokenCountEstimatorBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Override default client logger, used for HTTP request/response logging.
         */
        public CohereTokenCountEstimatorBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Whether to log HTTP requests.
         */
        public CohereTokenCountEstimatorBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log HTTP responses.
         */
        public CohereTokenCountEstimatorBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * The number of maximum sequential retries allowed if the request
         * fails.
         */
        public CohereTokenCountEstimatorBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public CohereTokenCountEstimator build() {
            return new CohereTokenCountEstimator(this);
        }
    }
}
