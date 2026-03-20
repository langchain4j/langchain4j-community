package dev.langchain4j.model.registry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents the cost structure for a model. Costs are typically per million
 * tokens.
 */
public class Cost {
    private Double input;
    private Double output;

    @JsonProperty("output_audio")
    private Double outputAudio;

    @JsonProperty("input_audio")
    private Double inputAudio;

    @JsonProperty("cache_read")
    private Double cacheRead;

    @JsonProperty("cache_write")
    private Double cacheWrite;

    public Cost() {}

    public Cost(Double input, Double output) {
        this.input = input;
        this.output = output;
    }

    /**
     * Gets the input cost per million tokens.
     *
     * @return the input cost, or {@code null} if not set
     */
    public Double getInput() {
        return input;
    }

    /**
     * Sets the input cost per million tokens.
     *
     * @param input the input cost to set
     */
    public void setInput(Double input) {
        this.input = input;
    }

    /**
     * Gets the output cost per million tokens.
     *
     * @return the output cost, or {@code null} if not set
     */
    public Double getOutput() {
        return output;
    }

    /**
     * Sets the output cost per million tokens.
     *
     * @param output the output cost to set
     */
    public void setOutput(Double output) {
        this.output = output;
    }

    /**
     * Gets the audio output cost per million tokens.
     * This is specifically for models that generate audio output.
     *
     * @return the audio output cost, or {@code null} if not applicable
     */
    public Double getOutputAudio() {
        return outputAudio;
    }

    /**
     * Sets the audio output cost per million tokens.
     *
     * @param outputAudio the audio output cost to set
     */
    public void setOutputAudio(Double outputAudio) {
        this.outputAudio = outputAudio;
    }

    /**
     * Gets the cost per million tokens for reading from cache.
     * Cache reads are typically less expensive than regular input tokens.
     *
     * @return the cache read cost, or {@code null} if caching is not supported
     */
    public Double getCacheRead() {
        return cacheRead;
    }

    /**
     * Sets the cost per million tokens for reading from cache.
     *
     * @param cacheRead the cache read cost to set
     */
    public void setCacheRead(Double cacheRead) {
        this.cacheRead = cacheRead;
    }

    /**
     * Gets the cost per million tokens for writing to cache.
     * Cache writes incur a cost when the model stores tokens for future reuse.
     *
     * @return the cache write cost, or {@code null} if caching is not supported
     */
    public Double getCacheWrite() {
        return cacheWrite;
    }

    /**
     * Sets the cost per million tokens for writing to cache.
     *
     * @param cacheWrite the cache write cost to set
     */
    public void setCacheWrite(Double cacheWrite) {
        this.cacheWrite = cacheWrite;
    }

    /**
     * Calculate total cost for a given number of input and output tokens.
     *
     * @param inputTokens  number of input tokens
     * @param outputTokens number of output tokens
     * @return total cost
     */
    public double calculateCost(long inputTokens, long outputTokens) {
        double totalCost = 0.0;
        if (input != null) {
            totalCost += (inputTokens / 1_000_000.0) * input;
        }
        if (output != null) {
            totalCost += (outputTokens / 1_000_000.0) * output;
        }
        return totalCost;
    }

    /**
     * Calculate cost including cache usage.
     *
     * @param inputTokens      number of input tokens
     * @param outputTokens     number of output tokens
     * @param cacheReadTokens  number of cache read tokens
     * @param cacheWriteTokens number of cache write tokens
     * @return total cost
     */
    public double calculateCostWithCache(
            long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        double totalCost = calculateCost(inputTokens, outputTokens);

        if (cacheRead != null) {
            totalCost += (cacheReadTokens / 1_000_000.0) * cacheRead;
        }
        if (cacheWrite != null) {
            totalCost += (cacheWriteTokens / 1_000_000.0) * cacheWrite;
        }

        return totalCost;
    }

    /**
     * Determines if this model is free to use (no input or output costs).
     *
     * @return {@code true} if both input and output costs are zero or null, {@code false} otherwise
     */
    public boolean isFree() {
        return (input == null || input == 0.0) && (output == null || output == 0.0);
    }

    /**
     * Gets the audio input cost per million tokens.
     * This is specifically for models that accept audio input.
     *
     * @return the audio input cost, or {@code null} if not applicable
     */
    public Double getInputAudio() {
        return inputAudio;
    }

    /**
     * Sets the audio input cost per million tokens.
     *
     * @param inputAudio the audio input cost to set
     */
    public void setInputAudio(Double inputAudio) {
        this.inputAudio = inputAudio;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheRead, cacheWrite, input, inputAudio, output, outputAudio);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Cost other = (Cost) obj;
        return Objects.equals(cacheRead, other.cacheRead)
                && Objects.equals(cacheWrite, other.cacheWrite)
                && Objects.equals(input, other.input)
                && Objects.equals(inputAudio, other.inputAudio)
                && Objects.equals(output, other.output)
                && Objects.equals(outputAudio, other.outputAudio);
    }

    @Override
    public String toString() {
        return "Cost [input=" + input + ", output=" + output + ", outputAudio=" + outputAudio + ", inputAudio="
                + inputAudio + ", cacheRead=" + cacheRead + ", cacheWrite=" + cacheWrite + "]";
    }
}
