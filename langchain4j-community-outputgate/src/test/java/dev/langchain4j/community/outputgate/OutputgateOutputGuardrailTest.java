package dev.langchain4j.community.outputgate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.content.Content;
import io.github.ravismash.outputgate.BudgetGate;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.SourceDocument;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputgateOutputGuardrailTest {

    private static final String SOURCE = "Of 120 people asked, 70 said yes and 50 said no.";

    private final OutputgateOutputGuardrail guardrail = new OutputgateOutputGuardrail(Contracts.survey());

    @Test
    void should_succeed_when_output_passes_the_contract() {
        OutputGuardrailResult result = guardrail.validate(request(Contracts.VALID));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_reprompt_with_every_violation_when_output_fails() {
        OutputGuardrailResult result = guardrail.validate(request(Contracts.WRONG_TOTAL));

        assertThat(result.result()).isEqualTo(GuardrailResult.Result.FATAL);
        assertThat(result.isReprompt()).isTrue();
        assertThat(result.getReprompt())
                .hasValueSatisfying(reprompt -> assertThat(reprompt)
                        .contains("did not pass these checks")
                        .contains(
                                "- total_responses: sum of options[*].count is 120 but total_responses is 130 (tolerance 0) (math)")
                        .contains("return the whole corrected JSON"));
        assertThat(result.<OutputGuardrailResult.Failure>failures())
                .singleElement()
                .satisfies(failure -> assertThat(failure.message())
                        .startsWith("The output failed its contract: total_responses: sum of"));
    }

    @Test
    void should_reprompt_when_output_is_not_json() {
        OutputGuardrailResult result = guardrail.validate(request("Sure! 70 yes and 50 no."));

        assertThat(result.isReprompt()).isTrue();
        assertThat(result.getReprompt())
                .hasValueSatisfying(reprompt ->
                        assertThat(reprompt).contains("output is not JSON").contains("(json-parse)"));
    }

    @Test
    void should_report_schema_violations() {
        OutputGuardrailResult result =
                guardrail.validate(request("{\"question\": \"Q\", \"total_responses\": \"many\", \"options\": []}"));

        assertThat(result.isReprompt()).isTrue();
        assertThat(result.getReprompt())
                .hasValueSatisfying(reprompt -> assertThat(reprompt).contains("(schema)"));
    }

    @Test
    void should_fail_fatally_without_reprompt_when_configured() {
        OutputgateOutputGuardrail fatal = OutputgateOutputGuardrail.builder()
                .contract(Contracts.survey())
                .repromptOnViolation(false)
                .build();

        OutputGuardrailResult result = fatal.validate(request(Contracts.WRONG_TOTAL));

        assertThat(result.isFatal()).isTrue();
        assertThat(result.isRetry()).isFalse();
        assertThat(result.isReprompt()).isFalse();
    }

    @Test
    void should_use_custom_reprompt_message() {
        OutputgateOutputGuardrail custom = OutputgateOutputGuardrail.builder()
                .contract(Contracts.survey())
                .repromptMessage(verdict -> "Fix " + verdict.violations().size() + " problem(s).")
                .build();

        OutputGuardrailResult result = custom.validate(request(Contracts.WRONG_TOTAL));

        assertThat(result.getReprompt()).contains("Fix 1 problem(s).");
    }

    @Test
    void should_unwrap_code_fence_and_pass_on_the_bare_json() {
        String fenced = "```json\n" + Contracts.VALID + "\n```";

        OutputGuardrailResult result = guardrail.validate(request(fenced));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.successfulText()).isEqualTo(Contracts.VALID);
    }

    @Test
    void should_check_fenced_text_as_is_when_unwrapping_is_off() {
        OutputgateOutputGuardrail strict = OutputgateOutputGuardrail.builder()
                .contract(Contracts.survey())
                .stripCodeFence(false)
                .build();

        OutputGuardrailResult result = strict.validate(request("```json\n" + Contracts.VALID + "\n```"));

        assertThat(result.isReprompt()).isTrue();
    }

    @Test
    void should_leave_text_without_a_complete_fence_unchanged() {
        assertThat(OutputgateOutputGuardrail.unwrapCodeFence("```json\n{}")).isEqualTo("```json\n{}");
        assertThat(OutputgateOutputGuardrail.unwrapCodeFence("{}")).isEqualTo("{}");
        assertThat(OutputgateOutputGuardrail.unwrapCodeFence("  ```\n{\"a\": 1}\n```  "))
                .isEqualTo("{\"a\": 1}");
    }

    @Test
    void should_reprompt_when_the_model_returns_no_text() {
        OutputGuardrailResult result = guardrail.validate(request(" "));

        assertThat(result.isReprompt()).isTrue();
        assertThat(result.getReprompt()).contains("Return the JSON object only.");
    }

    @Test
    void should_fail_fatally_on_missing_text_when_reprompt_is_off() {
        OutputgateOutputGuardrail fatal = OutputgateOutputGuardrail.builder()
                .contract(Contracts.survey())
                .repromptOnViolation(false)
                .build();

        assertThat(fatal.validate(request(" ")).isFatal()).isTrue();
        assertThat(fatal.validate(request(" ")).isReprompt()).isFalse();
    }

    @Test
    void should_check_quotes_against_the_user_message() {
        OutputgateOutputGuardrail grounded = OutputgateOutputGuardrail.builder()
                .contract(groundedContract())
                .source(OutputgateOutputGuardrail.userMessageAsSource())
                .build();

        assertThat(grounded.validate(request(quoting("70 said yes"), SOURCE)).isSuccess())
                .isTrue();
        OutputGuardrailResult invented = grounded.validate(request(quoting("80 said yes"), SOURCE));
        assertThat(invented.isReprompt()).isTrue();
        assertThat(invented.getReprompt())
                .hasValueSatisfying(
                        reprompt -> assertThat(reprompt).contains("finding").contains("(provenance)"));
    }

    @Test
    void should_check_quotes_against_retrieved_content() {
        OutputgateOutputGuardrail grounded = OutputgateOutputGuardrail.builder()
                .contract(groundedContract())
                .source(OutputgateOutputGuardrail.retrievedContentAsSource())
                .build();
        AugmentationResult augmentation = AugmentationResult.builder()
                .chatMessage(UserMessage.from("What did people say?"))
                .contents(List.of(
                        Content.from(TextSegment.from("Unrelated page.")), Content.from(TextSegment.from(SOURCE))))
                .build();

        OutputGuardrailResult onPageTwo = grounded.validate(request(quoting("70 said yes", 2), "q", augmentation));
        OutputGuardrailResult onPageOne = grounded.validate(request(quoting("70 said yes", 1), "q", augmentation));

        assertThat(onPageTwo.isSuccess()).isTrue();
        assertThat(onPageOne.isReprompt()).isTrue();
    }

    @Test
    void should_report_a_grounded_gate_without_source_as_a_violation() {
        OutputgateOutputGuardrail grounded = OutputgateOutputGuardrail.builder()
                .contract(groundedContract())
                .source(OutputgateOutputGuardrail.retrievedContentAsSource())
                .build();

        OutputGuardrailResult result = grounded.validate(request(quoting("70 said yes")));

        assertThat(result.isReprompt()).isTrue();
    }

    @Test
    void should_give_no_source_when_the_user_message_is_not_single_text() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(AiMessage.from("not a user message"))
                .build();
        OutputGuardrailRequest request = request(Contracts.VALID, chatRequest, null, null);

        SourceDocument source = OutputgateOutputGuardrail.userMessageAsSource().apply(request);

        assertThat(source).isNull();
    }

    @Test
    void should_pass_output_token_usage_to_budget_gates() {
        Contract budget = Contract.builder()
                .gate(BudgetGate.builder().maxOutputTokens(50).build())
                .build();
        OutputgateOutputGuardrail guardrail = new OutputgateOutputGuardrail(budget);

        assertThat(guardrail
                        .validate(request(Contracts.VALID, new TokenUsage(10, 40)))
                        .isSuccess())
                .isTrue();
        assertThat(guardrail
                        .validate(request(Contracts.VALID, new TokenUsage(10, 80)))
                        .isReprompt())
                .isTrue();
    }

    @Test
    void should_require_a_contract() {
        assertThatThrownBy(() -> OutputgateOutputGuardrail.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contract");
    }

    private static Contract groundedContract() {
        return Contract.builder()
                .gate(ProvenanceGate.builder().require("finding").grounded().build())
                .build();
    }

    private static String quoting(String quote) {
        return quoting(quote, 1);
    }

    private static String quoting(String quote, int page) {
        return "{\"finding\": {\"value\": \"" + quote + "\", \"source_span\": {\"page\": " + page + ", \"text\": \""
                + quote + "\"}}}";
    }

    private static OutputGuardrailRequest request(String output) {
        return request(output, "Summarize the survey.");
    }

    private static OutputGuardrailRequest request(String output, TokenUsage tokenUsage) {
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("q")).build();
        return request(output, chatRequest, null, tokenUsage);
    }

    private static OutputGuardrailRequest request(String output, String userMessage) {
        return request(output, userMessage, null);
    }

    private static OutputGuardrailRequest request(String output, String userMessage, AugmentationResult augmentation) {
        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();
        return request(output, chatRequest, augmentation, null);
    }

    private static OutputGuardrailRequest request(
            String output, ChatRequest chatRequest, AugmentationResult augmentation, TokenUsage tokenUsage) {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from(output))
                .tokenUsage(tokenUsage)
                .build();
        GuardrailRequestParams params = GuardrailRequestParams.builder()
                .userMessageTemplate("")
                .variables(Map.of())
                .augmentationResult(augmentation)
                .build();
        return OutputGuardrailRequest.builder()
                .responseFromLLM(response)
                .chatRequest(chatRequest)
                .chatExecutor(new UnusedChatExecutor())
                .requestParams(params)
                .build();
    }

    /** The guardrail never calls the model itself; reprompts go back through the AI service. */
    private static class UnusedChatExecutor implements ChatExecutor {

        @Override
        public ChatResponse execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse execute(List<ChatMessage> chatMessages) {
            throw new UnsupportedOperationException();
        }
    }
}
