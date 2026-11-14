package dev.langchain4j.community.tool.datacircle;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Calls the live Datacircle API, which needs no key.
 */
class DatacircleToolIT {

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    private final DatacircleTool tool = DatacircleTool.builder().build();

    @Test
    void shouldListTheApisFromTheRealApi() {
        String result = tool.listApis("email");

        assertThat(result).contains("(email-finder): ").contains(" APIs. Pass a slug to getBestPlans or searchPlans.");
    }

    @Test
    void shouldRankAnApisPlansFromTheRealApi() {
        String result = tool.getBestPlans("email-finder", 3);

        assertThat(result)
                .contains("(email-finder): the best 3 of ")
                .contains(System.lineSeparator() + "1. ")
                .contains("https://datacircle.dev/");
    }

    @Test
    void shouldSearchThePlansFromTheRealApi() {
        String result = tool.searchPlans("email-finder", null, "price", 3, 0);

        assertThat(result).startsWith("Found ").contains("; showing 1 to 3.");
    }

    @Test
    void shouldReadAProviderByNameFromTheRealApi() {
        String result = tool.getProvider("Bright Data");

        assertThat(result).startsWith("Bright Data (").contains("Its rank among each API's providers:");
    }

    @Test
    void shouldReturnTheApisOwnErrorOnAnUnknownSlug() {
        String result = tool.getBestPlans("no-such-api", null);

        assertThat(result)
                .startsWith("Error: Datacircle API request failed: HTTP 404.")
                .endsWith("Call listApis for every API's slug.");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void shouldAnswerWhichPlanIsBestThroughTheTool() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(true)
                .build();
        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result = assistant.chat(
                "Use the Datacircle tools to find the 3 best plans of the email-finder API, then name the best one.");

        assertThat(result.toolExecutions()).isNotEmpty();
        assertThat(result.toolExecutions())
                .extracting(execution -> execution.request().name())
                .contains("getBestPlans");
        ToolExecution best = result.toolExecutions().stream()
                .filter(execution -> execution.request().name().equals("getBestPlans"))
                .findFirst()
                .orElseThrow();
        assertThat(best.result()).contains("(email-finder): the best ");
        assertThat(result.content()).isNotBlank();
    }
}
