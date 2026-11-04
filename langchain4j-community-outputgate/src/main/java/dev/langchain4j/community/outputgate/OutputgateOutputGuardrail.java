package dev.langchain4j.community.outputgate;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.content.Content;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Usage;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.Violation;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An {@link OutputGuardrail} that checks structured (JSON) model output against an
 * <a href="https://github.com/ravismash/outputgate">outputgate</a> {@link Contract}.
 * <p>
 * A contract is an ordered list of deterministic gates: JSON Schema, arithmetic between fields,
 * regular expressions, quotes that must occur in a source document, and limits on output tokens.
 * No gate calls a model, so the same output always gets the same verdict.
 * <p>
 * When the output passes, the guardrail succeeds. When it fails, by default the guardrail asks
 * for a reprompt whose text lists every violation by field path, so the model can fix exactly
 * those fields; the number of attempts is the AI service's
 * {@link dev.langchain4j.guardrail.config.OutputGuardrailsConfig#maxRetries() maxRetries}. With
 * {@link Builder#repromptOnViolation(boolean) repromptOnViolation(false)} a failed check is fatal
 * instead.
 * <p>
 * A Markdown code fence around the JSON is removed before the check, and the unwrapped text is
 * passed on as the response.
 */
@Experimental
public class OutputgateOutputGuardrail implements OutputGuardrail {

    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s*```[A-Za-z0-9_-]*[ \\t]*\\R(.*?)\\R?[ \\t]*```\\s*$", Pattern.DOTALL);

    private final Contract contract;
    private final Function<OutputGuardrailRequest, SourceDocument> source;
    private final boolean repromptOnViolation;
    private final Function<Verdict, String> repromptMessage;
    private final boolean stripCodeFence;

    /**
     * Creates a guardrail with the default settings: no source document, reprompt on violation,
     * code fences removed.
     *
     * @param contract the contract the output must pass
     */
    public OutputgateOutputGuardrail(Contract contract) {
        this(builder().contract(contract));
    }

    private OutputgateOutputGuardrail(Builder builder) {
        this.contract = ensureNotNull(builder.contract, "contract");
        this.source = ensureNotNull(builder.source, "source");
        this.repromptOnViolation = builder.repromptOnViolation;
        this.repromptMessage = ensureNotNull(builder.repromptMessage, "repromptMessage");
        this.stripCodeFence = builder.stripCodeFence;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        ensureNotNull(request, "request");
        ChatResponse response = request.responseFromLLM();
        String text = response.aiMessage().text();
        if (text == null || text.isBlank()) {
            String message = "The model returned no text to check against the output contract";
            return repromptOnViolation ? reprompt(message, "Return the JSON object only.") : fatal(message);
        }

        String json = stripCodeFence ? unwrapCodeFence(text) : text;
        Verdict verdict = contract.evaluate(json, context(request, response));
        if (verdict.passed()) {
            return json.equals(text) ? success() : successWith(json);
        }

        String message = "The output failed its contract: " + describe(verdict.violations());
        return repromptOnViolation ? reprompt(message, repromptMessage.apply(verdict)) : fatal(message);
    }

    private EvalContext context(OutputGuardrailRequest request, ChatResponse response) {
        EvalContext context = EvalContext.empty().withUsage(usage(response.tokenUsage()));
        SourceDocument document = source.apply(request);
        return document == null ? context : context.withSource(document);
    }

    private static Usage usage(TokenUsage tokenUsage) {
        if (tokenUsage == null || tokenUsage.outputTokenCount() == null) {
            return Usage.UNKNOWN;
        }
        return new Usage(null, null, tokenUsage.outputTokenCount().longValue());
    }

    static String unwrapCodeFence(String text) {
        Matcher matcher = CODE_FENCE.matcher(text);
        return matcher.matches() ? matcher.group(1) : text;
    }

    private static String describe(List<Violation> violations) {
        return violations.stream().map(OutputgateOutputGuardrail::line).collect(Collectors.joining("; "));
    }

    private static String line(Violation violation) {
        return violation.path().isEmpty()
                ? violation.message() + " (" + violation.gate() + ")"
                : violation.path() + ": " + violation.message() + " (" + violation.gate() + ")";
    }

    /**
     * The default reprompt: every violation on its own line, then a request for the whole
     * corrected JSON.
     *
     * @param verdict the failed verdict
     * @return the text sent to the model
     */
    public static String defaultRepromptMessage(Verdict verdict) {
        String lines = verdict.violations().stream()
                .map(violation -> "- " + line(violation))
                .collect(Collectors.joining("\n"));
        return "Your previous response did not pass these checks:\n" + lines
                + "\nFix only what these checks report and return the whole corrected JSON, with no other text.";
    }

    /**
     * Uses the text of the last user message as the source document, for contracts with a
     * grounded provenance gate whose quotes come from the prompt.
     *
     * @return a source function for {@link Builder#source(Function)}
     */
    public static Function<OutputGuardrailRequest, SourceDocument> userMessageAsSource() {
        return request -> {
            List<ChatMessage> messages = request.chatRequest().messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof UserMessage userMessage && userMessage.hasSingleText()) {
                    return SourceDocument.of(userMessage.singleText());
                }
            }
            return null;
        };
    }

    /**
     * Uses the contents retrieved by RAG as the source document, one page per content, for
     * contracts with a grounded provenance gate. Page numbers start at 1 in retrieval order.
     *
     * @return a source function for {@link Builder#source(Function)}
     */
    public static Function<OutputGuardrailRequest, SourceDocument> retrievedContentAsSource() {
        return request -> {
            AugmentationResult augmentation = request.requestParams().augmentationResult();
            if (augmentation == null
                    || augmentation.contents() == null
                    || augmentation.contents().isEmpty()) {
                return null;
            }
            String[] pages = augmentation.contents().stream()
                    .map(Content::textSegment)
                    .map(segment -> segment.text())
                    .toArray(String[]::new);
            return SourceDocument.ofPages(pages);
        };
    }

    public static class Builder {

        private Contract contract;
        private Function<OutputGuardrailRequest, SourceDocument> source = request -> null;
        private boolean repromptOnViolation = true;
        private Function<Verdict, String> repromptMessage = OutputgateOutputGuardrail::defaultRepromptMessage;
        private boolean stripCodeFence = true;

        private Builder() {}

        /**
         * @param contract the contract the output must pass; required
         * @return this builder
         */
        public Builder contract(Contract contract) {
            this.contract = contract;
            return this;
        }

        /**
         * Where to find the source document for gates that check quotes against it. The function
         * may return {@code null} when there is none. See {@link #userMessageAsSource()} and
         * {@link #retrievedContentAsSource()}.
         *
         * @param source the source function
         * @return this builder
         */
        public Builder source(Function<OutputGuardrailRequest, SourceDocument> source) {
            this.source = source;
            return this;
        }

        /**
         * @param repromptOnViolation {@code true} (default) to reprompt the model with the
         *     violations, {@code false} to fail fatally
         * @return this builder
         */
        public Builder repromptOnViolation(boolean repromptOnViolation) {
            this.repromptOnViolation = repromptOnViolation;
            return this;
        }

        /**
         * @param repromptMessage builds the reprompt text from a failed verdict; defaults to
         *     {@link #defaultRepromptMessage(Verdict)}
         * @return this builder
         */
        public Builder repromptMessage(Function<Verdict, String> repromptMessage) {
            this.repromptMessage = repromptMessage;
            return this;
        }

        /**
         * @param stripCodeFence {@code true} (default) to remove a Markdown code fence around the
         *     JSON before checking it
         * @return this builder
         */
        public Builder stripCodeFence(boolean stripCodeFence) {
            this.stripCodeFence = stripCodeFence;
            return this;
        }

        public OutputgateOutputGuardrail build() {
            return new OutputgateOutputGuardrail(this);
        }
    }
}
