package dev.langchain4j.community.model.client.transcription;

import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.data.audio.Audio;
import java.util.Objects;

public class CohereTranscriptionRequest {

    private final String model;
    private final String language;
    private final Double temperature;
    private final Audio audio;

    private CohereTranscriptionRequest(Builder builder) {
        this.model = builder.model;
        this.language = builder.language;
        this.temperature = builder.temperature;
        this.audio = builder.audio;
    }

    public String getModel() {
        return model;
    }

    public String getLanguage() {
        return language;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Audio getAudio() {
        return audio;
    }

    @Override
    public String toString() {
        return "CohereTranscriptionRequest{ "
                + "model=" + quoted(model)
                + ", language=" + quoted(language)
                + ", temperature=" + temperature
                + ", audio=" + audio
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, language, temperature, audio);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereTranscriptionRequest other && equalsTo(other);
    }

    private boolean equalsTo(CohereTranscriptionRequest other) {
        return Objects.equals(model, other.model)
                && Objects.equals(language, other.language)
                && Objects.equals(temperature, other.temperature)
                && Objects.equals(audio, other.audio);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String model;
        private String language;
        private Double temperature;
        private Audio audio;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder audio(Audio audio) {
            this.audio = audio;
            return this;
        }

        public CohereTranscriptionRequest build() {
            return new CohereTranscriptionRequest(this);
        }
    }
}
