package dev.langchain4j.community.model;

import static dev.langchain4j.community.model.util.CohereAudioUtils.validate;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.transcription.CohereTranscriptionRequest;
import dev.langchain4j.community.model.client.transcription.CohereTranscriptionResponse;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Represents a Cohere model with a speech-to-text interface
 * <p>
 * This implementation is based on Cohere V2's transcription API
 *
 * @see <a href="https://docs.cohere.com/reference/create-audio-transcription">Cohere V2's transcription API specification</a>
 * @since 1.15.0
 */
@Experimental
public class CohereAudioTranscriptionModel implements AudioTranscriptionModel {

    private final CohereClient cohereClient;
    private final String modelName;
    private final String language;
    private final Double temperature;
    private final Integer maxRetries;

    private CohereAudioTranscriptionModel(Builder builder) {
        this.cohereClient = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.cohere.com/v2/"))
                .authToken(builder.apiKey)
                .timeout(builder.timeout)
                .logger(builder.logger)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();

        this.modelName = ensureNotBlank(builder.modelName, "model name");
        this.language = builder.language;
        this.temperature = builder.temperature;
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    @Override
    public AudioTranscriptionResponse transcribe(AudioTranscriptionRequest audioTranscriptionRequest) {

        if (audioTranscriptionRequest == null || audioTranscriptionRequest.audio() == null) {
            throw illegalArgument("Request and audio are required");
        }

        CohereTranscriptionRequest request = CohereTranscriptionRequest.builder()
                .model(modelName)
                .language(getOrDefault(audioTranscriptionRequest.language(), language))
                .temperature(getOrDefault(audioTranscriptionRequest.temperature(), temperature))
                .audio(audioTranscriptionRequest.audio())
                .build();

        validate(request);

        CohereTranscriptionResponse response =
                withRetryMappingExceptions(() -> cohereClient.createTranscription(request), maxRetries);

        return AudioTranscriptionResponse.from(response.getText());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private String language;
        private Double temperature;
        private Duration timeout;
        private Integer maxRetries;

        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        /**
         * The base URL of the Cohere API.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The Cohere API key.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The ID of the model to use.
         */
        public Builder modelName(String model) {
            this.modelName = model;
            return this;
        }

        /**
         * The language of the input audio, supplied in
         * <a href="https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes">ISO-639-1</a> format
         * (for example, {@code en}).
         * Must be specified if not defined in {@link AudioTranscriptionRequest}.
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * The sampling temperature. Higher values make the output more random,
         * while lower ones make it more deterministic.
         * <p>
         * Must be between {@code 0} and {@code 1}.
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * The number of maximum sequential retries allowed if the request
         * fails.
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * The timeout value for the request.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Whether to log HTTP requests.
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Whether to log HTTP responses.
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Override default client logger, used for HTTP request/response logging.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public CohereAudioTranscriptionModel build() {
            return new CohereAudioTranscriptionModel(this);
        }
    }
}
