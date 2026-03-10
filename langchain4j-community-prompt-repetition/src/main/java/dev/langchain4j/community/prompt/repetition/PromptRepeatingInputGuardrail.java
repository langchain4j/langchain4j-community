package dev.langchain4j.community.prompt.repetition;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Repeats single-text user input before sending it to the model.
 * <p>
 * This guardrail is intentionally conservative:
 * - It skips non-single-text messages (multimodal or multi-part text)
 * - It skips inputs that are already augmented via RAG by default
 */
public class PromptRepeatingInputGuardrail implements InputGuardrail {

    public static final boolean DEFAULT_ALLOW_RAG_INPUT = false;

    private final PromptRepetitionPolicy policy;
    private final boolean allowRagInput;

    public PromptRepeatingInputGuardrail() {
        this(new PromptRepetitionPolicy(), DEFAULT_ALLOW_RAG_INPUT);
    }

    public PromptRepeatingInputGuardrail(PromptRepetitionPolicy policy) {
        this(policy, DEFAULT_ALLOW_RAG_INPUT);
    }

    public PromptRepeatingInputGuardrail(PromptRepetitionPolicy policy, boolean allowRagInput) {
        this.policy = ensureNotNull(policy, "policy");
        this.allowRagInput = allowRagInput;
    }

    /**
     * Computes a repetition decision for the given request.
     */
    public PromptRepetitionDecision decide(InputGuardrailRequest request) {
        ensureNotNull(request, "request");

        UserMessage userMessage = request.userMessage();
        if (!userMessage.hasSingleText()) {
            return PromptRepetitionDecision.skipped(
                    PromptRepetitionReason.SKIPPED_NON_TEXT, firstTextOrEmpty(userMessage));
        }

        String text = userMessage.singleText();
        if (!allowRagInput && request.requestParams().augmentationResult() != null) {
            return PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_RAG_DETECTED, text);
        }

        return policy.decide(text);
    }

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        PromptRepetitionDecision decision = decide(request);
        return decision.applied() ? successWith(decision.text()) : success();
    }

    public PromptRepetitionPolicy policy() {
        return policy;
    }

    public boolean allowRagInput() {
        return allowRagInput;
    }

    private String firstTextOrEmpty(UserMessage userMessage) {
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .findFirst()
                .orElse("");
    }
}
