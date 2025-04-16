package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Neo4jGraphConverterTest extends Neo4jGraphConverterBaseTest {

    @Mock
    private static ChatLanguageModel model;

    @Override
    ChatLanguageModel getModel() {
        final String keanuStringResponse =
                "[{\"head_type\":\"Person\",\"text\":\"Keanu Reeves acted in Matrix\",\"relation\":\"ACTED_IN\",\"tail_type\":\"Movie\",\"tail\":\"Matrix\",\"head\":\"Keanu Reeves\"}]";
        final ChatResponse chatResponseKeanu = new ChatResponse.Builder()
                .aiMessage(new AiMessage(keanuStringResponse))
                .build();
        final List<ChatMessage> keanuMessages =
                argThat(arg -> arg != null && arg.toString().toLowerCase().contains("keanu"));
        when(model.chat(keanuMessages)).thenReturn(chatResponseKeanu);

        final String sylvesterStringResponse =
                "[{\"tail_type\":\"Location\",\"tail\":\"table\",\"head\":\"Sylvester the cat\",\"head_type\":\"Animal\",\"text\":\"Sylvester the cat is on the table\",\"relation\":\"IS_ON\"}]";
        final ChatResponse chatResponseSylvester = new ChatResponse.Builder()
                .aiMessage(new AiMessage(sylvesterStringResponse))
                .build();
        final List<ChatMessage> sylvesterMessages =
                argThat(arg -> arg != null && arg.toString().toLowerCase().contains("sylvester"));
        when(model.chat(sylvesterMessages)).thenReturn(chatResponseSylvester);

        return model;
    }
}
