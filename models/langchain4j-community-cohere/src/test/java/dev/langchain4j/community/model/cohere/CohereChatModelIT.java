package dev.langchain4j.community.model.cohere;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

import java.util.List;

public class CohereChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                CohereChatModel.builder()
                        .modelName("command-r7b-12-2024")
                        .authToken("6dWN6j6kD4ntSLZb9WlINBax4OFFxzFufdkRr6A7")
                        .build()
        );
    }


    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        CohereChatModel.Builder cohereChatModelBuilder = CohereChatModel.builder()
                .authToken("6dWN6j6kD4ntSLZb9WlINBax4OFFxzFufdkRr6A7")
                .defaultRequestParameters(parameters);
                //.logRequests(true)
                //.logResponses(true);

        if (parameters.modelName() == null) {
            cohereChatModelBuilder.modelName("command-r7b-12-2024");
        }
        return cohereChatModelBuilder.build();
    }

    // TODO: Support token usage in the future
    @Override
    protected boolean assertTokenUsage() { return false; }
}
