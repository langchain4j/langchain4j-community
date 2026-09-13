package dev.langchain4j.community.tool.livetennis;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "LIVE_TENNIS_API_KEY", matches = ".+")
class LiveTennisToolIT {

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @Test
    void shouldReadTheLiveSlateFromTheRealApi() {
        LiveTennisTool tool = LiveTennisTool.builder()
                .apiKey(System.getenv("LIVE_TENNIS_API_KEY"))
                .build();

        String result = tool.getLiveMatches(null, "singles", 3);

        assertThat(result).isNotBlank().doesNotStartWith("Error:");
    }

    @Test
    void shouldListUpcomingFixturesFromTheRealApi() {
        LiveTennisTool tool = LiveTennisTool.builder()
                .apiKey(System.getenv("LIVE_TENNIS_API_KEY"))
                .build();

        String result = tool.getUpcomingFixtures("atp", "singles", 3);

        assertThat(result).isNotBlank().doesNotStartWith("Error:");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void shouldAnswerALiveTennisQuestionThroughTheTool() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(true)
                .build();
        LiveTennisTool tool = LiveTennisTool.builder()
                .apiKey(System.getenv("LIVE_TENNIS_API_KEY"))
                .build();
        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result =
                assistant.chat("Use the live tennis tool to list up to 3 tennis matches in play now, then summarize.");

        assertThat(result.toolExecutions()).isNotEmpty();
        ToolExecution execution = result.toolExecutions().get(0);
        assertThat(execution.request().name()).isEqualTo("getLiveMatches");
        assertThat(execution.result()).isNotBlank();
        assertThat(result.content()).isNotBlank();
    }
}
