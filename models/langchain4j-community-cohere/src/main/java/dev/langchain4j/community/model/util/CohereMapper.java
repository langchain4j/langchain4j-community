package dev.langchain4j.community.model.util;

import dev.langchain4j.Internal;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.message.CohereContentType;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;
import dev.langchain4j.community.model.client.chat.message.CohereMessageContent;
import dev.langchain4j.community.model.client.chat.message.CohereMessageTextContent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

import static dev.langchain4j.community.model.client.chat.message.CohereRole.ASSISTANT;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.SYSTEM;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.USER;

@Internal
public class CohereMapper {

    private CohereMapper() {}

    public static CohereChatRequest toCohereChatRequest(ChatRequest chatRequest) {
        return CohereChatRequest.builder()
                .model(chatRequest.modelName())
                .messages(toCohereChatMessages(chatRequest.messages()))
                .temperature(chatRequest.temperature())
                .p(chatRequest.topP())
                .k(chatRequest.topK())
                .presencePenalty(chatRequest.presencePenalty())
                .frequencyPenalty(chatRequest.frequencyPenalty())
                .maxOutputTokens(chatRequest.maxOutputTokens())
                .stopSequences(chatRequest.stopSequences())
                .build();
    }

    private static List<CohereMessage> toCohereChatMessages(List<ChatMessage> chatMessages) {
        return chatMessages.stream()
                .map(CohereMapper::toCohereChatMessage)
                .toList();
    }

    private static CohereMessage toCohereChatMessage(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage userMessage) {
            return new CohereMessage(USER, toCohereTextContent(userMessage.contents()));
        }

        if (chatMessage instanceof AiMessage aiMessage) {
            return new CohereMessage(ASSISTANT, List.of(new CohereMessageTextContent(aiMessage.text())));
        }

        if (chatMessage instanceof SystemMessage systemMessage) {
            return new CohereMessage(SYSTEM, List.of(new CohereMessageTextContent(systemMessage.text())));
        }

        throw new LangChain4jException("Unexpected message type: " + chatMessage.getClass());
    }

    private static List<CohereMessageContent> toCohereTextContent(List<Content> contents) {
        return contents.stream()
                .filter(TextContent.class::isInstance)
                .map(c -> (CohereMessageContent) new CohereMessageTextContent(((TextContent ) c).text()))
                .toList();
    }

    public static ChatResponse fromCohereChatResponse(CohereChatResponse cohereChatResponse) {
        String text = cohereChatResponse.message.getContent().stream()
                .filter(c -> c.type == CohereContentType.TEXT)
                .findFirst()
                .map(c -> c.text)
                .orElseThrow(() -> new LangChain4jException("No text content found!"));

        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}
