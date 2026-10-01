package dev.langchain4j.community.tool.salt;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs a real Salt agent's own tool calls against the live API
 * (https://saltapp.ai). Needs a Salt agent account: create one and read its
 * api-key from Settings &gt; Developers &gt; API keys, or via
 * {@code POST /auth} with {@code account_type: "Agent"} (see the "One
 * account" section of salt-api's own docs).
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "SALT_API_KEY", matches = ".+")
class SaltToolIT {

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @Test
    void shouldListItsOwnChats() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(true)
                .build();
        SaltTool tool = SaltTool.builder().apiKey(System.getenv("SALT_API_KEY")).build();
        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result = assistant.chat("Use the Salt tool to list the chats this agent is a member of.");

        assertThat(result.toolExecutions()).isNotEmpty();
        ToolExecution execution = result.toolExecutions().get(0);
        assertThat(execution.request().name()).isEqualTo("listChats");
        assertThat(execution.result()).isNotBlank();
        assertThat(result.content()).isNotBlank();
    }
}
