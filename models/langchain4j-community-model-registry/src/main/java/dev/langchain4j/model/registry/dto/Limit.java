package dev.langchain4j.model.registry.dto;

import java.util.Objects;

/**
 * Represents the token limits for a model.
 */
public class Limit {

    private Integer context;
    private Integer input;
    private Integer output;

    public Limit() {}

    public Limit(Integer context, Integer input, Integer output) {
        this.context = context;
        this.input = input;
        this.output = output;
    }

    /**
     * Gets the maximum context window size in tokens.
     * This represents the total number of tokens (input + output) the model can handle.
     *
     * @return the context limit, or {@code null} if not specified
     */
    public Integer getContext() {
        return context;
    }

    /**
     * Sets the maximum context window size in tokens.
     *
     * @param context the context limit to set
     */
    public void setContext(Integer context) {
        this.context = context;
    }

    /**
     * Gets the maximum number of input tokens.
     *
     * @return the input token limit, or {@code null} if not specified
     */
    public Integer getInput() {
        return input;
    }

    /**
     * Sets the maximum number of input tokens.
     *
     * @param input the input token limit to set
     */
    public void setInput(Integer input) {
        this.input = input;
    }

    /**
     * Gets the maximum number of output tokens the model can generate.
     *
     * @return the output token limit, or {@code null} if not specified
     */
    public Integer getOutput() {
        return output;
    }

    /**
     * Sets the maximum number of output tokens.
     *
     * @param output the output token limit to set
     */
    public void setOutput(Integer output) {
        this.output = output;
    }

    /**
     * Checks if this model can handle the specified token requirements.
     * A null limit means no constraint for that dimension.
     *
     * @param requiredContext the required context window size
     * @param requiredInput   the required input token count
     * @param requiredOutput  the required output token count
     * @return {@code true} if all requirements can be met, {@code false} otherwise
     */
    public boolean canHandle(int requiredContext, int requiredInput, int requiredOutput) {
        boolean contextOk = context == null || context >= requiredContext;
        boolean inputOk = input == null || input >= requiredInput;
        boolean outputOk = output == null || output >= requiredOutput;

        return contextOk && inputOk && outputOk;
    }

    /**
     * Checks if this model has a large context window (100,000+ tokens).
     *
     * @return {@code true} if context is at least 100,000 tokens, {@code false} otherwise
     */
    public boolean hasLargeContext() {
        return context != null && context >= 100_000;
    }

    /**
     * Checks if this model has an extended context window (200,000+ tokens).
     *
     * @return {@code true} if context is at least 200,000 tokens, {@code false} otherwise
     */
    public boolean hasExtendedContext() {
        return context != null && context >= 200_000;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Limit limit = (Limit) o;
        return Objects.equals(context, limit.context)
                && Objects.equals(input, limit.input)
                && Objects.equals(output, limit.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, input, output);
    }

    @Override
    public String toString() {
        return "Limit{" + "context=" + context + ", input=" + input + ", output=" + output + '}';
    }
}
