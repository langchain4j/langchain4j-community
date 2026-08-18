package dev.langchain4j.community.tool.xquik;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "XQUIK_API_KEY", matches = ".+")
class XquikToolIT {

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @Test
    void shouldSearchPublicTweets() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(true)
                .build();
        XquikTool tool =
                XquikTool.builder().apiKey(System.getenv("XQUIK_API_KEY")).build();
        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).tools(tool).build();

        Result<String> result = assistant.chat(
                "Use the Xquik search tool to find up to 3 latest public posts about LangChain4j. Summarize the result.");

        assertThat(result.toolExecutions()).isNotEmpty();
        ToolExecution execution = result.toolExecutions().get(0);
        assertThat(execution.request().name()).isEqualTo("searchTweets");
        assertThat(execution.result()).isNotBlank();
        assertThat(result.content()).isNotBlank();
    }
}
