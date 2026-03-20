package dev.langchain4j.model.registry.dto;

import java.util.List;
import java.util.Objects;

/**
 * Represents the input/output modalities supported by a model.
 */
public class Modalities {
    private List<String> input;
    private List<String> output;

    public Modalities() {}

    public Modalities(List<String> input, List<String> output) {
        this.input = input;
        this.output = output;
    }

    /**
     * Gets the list of supported input modalities.
     *
     * @return a list of input modality types (e.g., "text", "image", "audio", "video")
     */
    public List<String> getInput() {
        return input;
    }

    /**
     * Sets the list of supported input modalities.
     *
     * @param input the list of input modalities to set
     */
    public void setInput(List<String> input) {
        this.input = input;
    }

    /**
     * Gets the list of supported output modalities.
     *
     * @return a list of output modality types (e.g., "text", "image", "audio", "video")
     */
    public List<String> getOutput() {
        return output;
    }

    /**
     * Sets the list of supported output modalities.
     *
     * @param output the list of output modalities to set
     */
    public void setOutput(List<String> output) {
        this.output = output;
    }

    /**
     * Checks if a specific input modality is supported.
     *
     * @param modality the modality type to check (e.g., "text", "image", "audio", "video")
     * @return {@code true} if the modality is supported for input, {@code false} otherwise
     */
    public boolean supportsInputModality(String modality) {
        return input != null && input.contains(modality);
    }

    /**
     * Checks if a specific output modality is supported.
     *
     * @param modality the modality type to check (e.g., "text", "image", "audio", "video")
     * @return {@code true} if the modality is supported for output, {@code false} otherwise
     */
    public boolean supportsOutputModality(String modality) {
        return output != null && output.contains(modality);
    }

    /**
     * Checks if text modality is supported for input or output.
     *
     * @return {@code true} if text is supported, {@code false} otherwise
     */
    public boolean supportsText() {
        return supportsInputModality("text") || supportsOutputModality("text");
    }

    /**
     * Checks if image modality is supported for input or output.
     *
     * @return {@code true} if image is supported, {@code false} otherwise
     */
    public boolean supportsImage() {
        return supportsInputModality("image") || supportsOutputModality("image");
    }

    /**
     * Checks if video modality is supported for input or output.
     *
     * @return {@code true} if video is supported, {@code false} otherwise
     */
    public boolean supportsVideo() {
        return supportsInputModality("video") || supportsOutputModality("video");
    }

    /**
     * Checks if audio modality is supported for input or output.
     *
     * @return {@code true} if audio is supported, {@code false} otherwise
     */
    public boolean supportsAudio() {
        return supportsInputModality("audio") || supportsOutputModality("audio");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Modalities that = (Modalities) o;
        return Objects.equals(input, that.input) && Objects.equals(output, that.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, output);
    }

    @Override
    public String toString() {
        return "Modalities{" + "input=" + input + ", output=" + output + '}';
    }
}
