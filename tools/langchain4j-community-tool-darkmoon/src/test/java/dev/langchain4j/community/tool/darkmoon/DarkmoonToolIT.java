package dev.langchain4j.community.tool.darkmoon;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Lets a chat model call the Darkmoon tools against a real, self-hosted
 * Darkmoon dashboard. Needs a Darkmoon instance with the web dashboard (a Pro
 * feature) and a dashboard user: set {@code DARKMOON_BASE_URL},
 * {@code DARKMOON_USERNAME} and {@code DARKMOON_PASSWORD}.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DARKMOON_BASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DARKMOON_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DARKMOON_PASSWORD", matches = ".+")
class DarkmoonToolIT {

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @Test
    void shouldListCampaigns() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(true)
                .build();
        DarkmoonTool tool = DarkmoonTool.builder()
                .baseUrl(System.getenv("DARKMOON_BASE_URL"))
                .username(System.getenv("DARKMOON_USERNAME"))
                .password(System.getenv("DARKMOON_PASSWORD"))
                .build();
        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result = assistant.chat("Use the Darkmoon tool to list the pentest campaigns.");

        assertThat(result.toolExecutions()).isNotEmpty();
        ToolExecution execution = result.toolExecutions().get(0);
        assertThat(execution.request().name()).isEqualTo("listDarkmoonCampaigns");
        assertThat(execution.result()).isNotBlank();
        assertThat(result.content()).isNotBlank();
    }
}
