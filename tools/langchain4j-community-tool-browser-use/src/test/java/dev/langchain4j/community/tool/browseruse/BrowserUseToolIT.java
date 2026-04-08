package dev.langchain4j.community.tool.browseruse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class BrowserUseToolIT {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(OpenAiChatModelName.GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_execute_tool() {
        final MockBrowserExecutionEngine mockBrowserExecutionEngine = spy(new MockBrowserExecutionEngine());
        BrowserUseTool tool = BrowserUseTool.from(mockBrowserExecutionEngine);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.chat("open page 'https://docs.langchain4j.dev/', and summary the page text");

        assertThat(answer).contains("Java");
        assertThat(answer).contains("LLM");

        verify(mockBrowserExecutionEngine).navigate("https://docs.langchain4j.dev/");
        verify(mockBrowserExecutionEngine).getText();
    }
}
