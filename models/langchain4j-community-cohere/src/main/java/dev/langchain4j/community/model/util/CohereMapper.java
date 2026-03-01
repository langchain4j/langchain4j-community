package dev.langchain4j.community.model.util;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.message.content.CohereContentType;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;
import dev.langchain4j.community.model.client.chat.message.content.CohereMessageContent;
import dev.langchain4j.community.model.client.chat.message.content.CohereMessageTextContent;
import dev.langchain4j.community.model.client.chat.tool.CohereFunction;
import dev.langchain4j.community.model.client.chat.tool.CohereTool;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;
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
import java.util.Optional;

import static dev.langchain4j.community.model.client.chat.message.CohereRole.ASSISTANT;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.SYSTEM;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.USER;
import static dev.langchain4j.community.model.client.chat.message.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.tool.CohereToolType.FUNCTION;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;

@Internal
public class CohereMapper {

    private CohereMapper() {}

    public static CohereChatRequest toCohereChatRequest(ChatRequest chatRequest) {
        return CohereChatRequest.builder()
                .model(chatRequest.modelName())
                .messages(toCohereChatMessages(chatRequest.messages()))
                .tools(chatRequest.toolSpecifications()
                        .stream()
                        .map(CohereMapper::toCohereTool)
                        .toList())
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

    private static CohereTool toCohereTool(ToolSpecification toolSpecification) {
        return CohereTool.builder()
                .type(FUNCTION)
                .function(CohereFunction.builder()
                        .name(toolSpecification.name())
                        .parameters(toMap(toolSpecification.parameters()))
                        .description(toolSpecification.description())
                        .build())
                .build();
    }

    public static ChatResponse fromCohereChatResponse(CohereChatResponse cohereChatResponse) {
        Optional<List<CohereMessageContent>> content = Optional.ofNullable(cohereChatResponse.message.getContent());

        String text = null;

        if (content.isPresent()) {
            text = content.get().stream()
                    .filter(c -> c.type == TEXT)
                    .findFirst()
                    .map(c -> c.text)
                    .orElse(null);
        }

        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(text)
                        .toolExecutionRequests(cohereChatResponse.message.getToolCalls()
                                .stream()
                                .map(CohereMapper::toToolExecutionRequest)
                                .toList())
                        .build())
                .build();
    }

    private static ToolExecutionRequest toToolExecutionRequest(CohereToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(toolCall.getFunction().getName())
                .arguments(toolCall.getFunction().getArguments())
                .build();
    }
}
