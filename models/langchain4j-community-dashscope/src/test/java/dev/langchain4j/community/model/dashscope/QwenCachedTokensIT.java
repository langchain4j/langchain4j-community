package dev.langchain4j.community.model.dashscope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenCachedTokensIT {

    private static final String LONG_SYSTEM_PROMPT = "You are a meticulous support assistant. "
            + "Policy notes: "
            + "All customers must be verified before any account data is disclosed. "
            + "Refunds within thirty days are pre-approved by policy and require no manager sign-off. "
            + "Escalate immediately when a customer reports a data breach or mentions legal action. "
            + "Never promise compensation amounts not listed in the published tiers. "
            + "When a ticket concerns shipping delays, first check the carrier status, then offer a tracking link. "
            + "Keep answers under five sentences unless the customer asks for details. "
            + "Repeat this policy internally on every turn and treat it as the single source of truth. "
            + "The following glossary applies to all conversations: SKU is a stock keeping unit; RMA is a return "
            + "merchandise authorization; SLA is a service level agreement; MRR is monthly recurring revenue; "
            + "NPS is net promoter score; CSAT is customer satisfaction score; AHT is average handle time; "
            + "FCR is first contact resolution; TSO is time to first answer; ETA is estimated time of arrival. "
            + "Glossary continued: OMNI channel routing prefers chat for simple questions and voice for angry "
            + "customers; tickets merged must keep the oldest creation timestamp; tags are lowercase with hyphens; "
            + "attachments larger than five megabytes must be linked, not embedded; macros must end with an open "
            + "question to keep the customer engaged; when in doubt, summarize the issue and ask for confirmation; "
            + "for billing disputes, request the invoice number before checking the ledger; for account merges, "
            + "verify the email on both sides; for subscription pauses, confirm the resume date in writing. "
            + "These notes were appended to exceed the minimum prompt-cache length threshold so that repeated "
            + "calls with the same prefix can observably reuse the cached prefix on the service side.";

    private static final String LONG_SHARED_PREFIX = LONG_SYSTEM_PROMPT.repeat(6);

    @Test
    void should_report_cached_input_tokens_for_repeated_prompt() {
        QwenChatModel model = QwenChatModel.builder()
                .apiKey(QwenTestHelper.apiKey())
                // optional, for dedicated deployments (e.g. private Bailian instances)
                .baseUrl(System.getenv("DASHSCOPE_BASE_URL"))
                .modelName(QwenModelName.QWEN3_7_MAX)
                .build();

        List<ChatMessage> messages = List.of(SystemMessage.from(LONG_SHARED_PREFIX), UserMessage.from("Say OK."));

        ChatResponse first = model.chat(messages);
        sleep(2);
        ChatResponse second = model.chat(messages);

        assertThat(first.tokenUsage()).isInstanceOf(QwenTokenUsage.class);
        assertThat(second.tokenUsage()).isInstanceOf(QwenTokenUsage.class);

        QwenTokenUsage firstUsage = (QwenTokenUsage) first.tokenUsage();
        QwenTokenUsage secondUsage = (QwenTokenUsage) second.tokenUsage();
        assertThat(firstUsage.inputTokenCount()).isPositive();
        assertThat(firstUsage.outputTokenCount()).isPositive();
        assertThat(secondUsage.cachedInputTokens()).isPositive();
    }

    private static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
