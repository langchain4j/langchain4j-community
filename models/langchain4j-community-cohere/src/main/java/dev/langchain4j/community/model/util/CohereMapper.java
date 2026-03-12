package dev.langchain4j.community.model.util;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.CohereResponseFormat;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;
import dev.langchain4j.community.model.client.chat.message.content.CohereImageContent;
import dev.langchain4j.community.model.client.chat.message.content.CohereImageUrl;
import dev.langchain4j.community.model.client.chat.message.content.CohereMessageContent;
import dev.langchain4j.community.model.client.chat.message.content.CohereMessageTextContent;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.thinking.CohereThinking;
import dev.langchain4j.community.model.client.chat.thinking.CohereThinkingType;
import dev.langchain4j.community.model.client.chat.tool.CohereFunction;
import dev.langchain4j.community.model.client.chat.tool.CohereFunctionCall;
import dev.langchain4j.community.model.client.chat.tool.CohereTool;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.community.model.client.CohereResponseFormatType.JSON_OBJECT;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.ASSISTANT;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.SYSTEM;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.TOOL;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.USER;
import static dev.langchain4j.community.model.client.chat.message.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.thinking.CohereThinkingType.DISABLED;
import static dev.langchain4j.community.model.client.chat.thinking.CohereThinkingType.ENABLED;
import static dev.langchain4j.community.model.client.chat.tool.CohereToolType.FUNCTION;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.lang.Boolean.TRUE;

@Internal
public class CohereMapper {

    private static final Map<String, Object> EMPTY_SCHEMA = toMap(JsonObjectSchema.builder().build());

    private CohereMapper() {}

    public static CohereChatRequest toCohereChatRequest(ChatRequest chatRequest, Boolean enableThinking, Integer thinkingTokenBudget) {

        CohereChatRequest.Builder builder = CohereChatRequest.builder()
                .model(chatRequest.modelName())
                .messages(toCohereChatMessages(chatRequest.messages()))
                .responseFormat(toCohereResponseFormat(chatRequest.responseFormat()))
                .tools(chatRequest.toolSpecifications()
                        .stream()
                        .map(CohereMapper::toCohereTool)
                        .toList())
                .toolChoice(chatRequest.toolChoice())
                .temperature(chatRequest.temperature())
                .p(chatRequest.topP())
                .k(chatRequest.topK())
                .presencePenalty(chatRequest.presencePenalty())
                .frequencyPenalty(chatRequest.frequencyPenalty())
                .maxTokens(chatRequest.maxOutputTokens())
                .stopSequences(chatRequest.stopSequences());

        if (enableThinking != null || thinkingTokenBudget != null) {
            builder.thinking(CohereThinking.builder()
                            .type(TRUE.equals(enableThinking) ? ENABLED : DISABLED)
                            .tokenBudget(thinkingTokenBudget)
                            .build());
        }

        return builder.build();
    }

    private static List<CohereMessage> toCohereChatMessages(List<ChatMessage> chatMessages) {
        return chatMessages.stream()
                .map(CohereMapper::toCohereChatMessage)
                .toList();
    }

    private static CohereMessage toCohereChatMessage(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage userMessage) {
            return CohereMessage.builder()
                    .role(USER)
                    .content(userMessage.contents().stream()
                            .filter(c -> c instanceof TextContent || c instanceof ImageContent)
                            .map(CohereMapper::toCohereContent)
                            .toList())
                    .build();
        }

        if (chatMessage instanceof AiMessage aiMessage) {
            CohereMessage.Builder builder = CohereMessage.builder()
                    .role(ASSISTANT)
                    .content(List.of(new CohereMessageTextContent(aiMessage.text())));

            if (aiMessage.hasToolExecutionRequests()) {
                List<CohereToolCall> toolCalls = aiMessage.toolExecutionRequests().stream()
                        .map(tc -> CohereToolCall.builder()
                                .type(FUNCTION)
                                .id(tc.id())
                                .function(CohereFunctionCall.builder()
                                        .name(tc.name())
                                        .arguments(tc.arguments())
                                        .build())
                                .build())
                        .toList();
                builder.toolCalls(toolCalls);
            }

            return builder.build();
        }

        if (chatMessage instanceof SystemMessage systemMessage) {
            return CohereMessage.builder()
                    .role(SYSTEM)
                    .content(List.of(new CohereMessageTextContent(systemMessage.text())))
                    .build();
        }

        if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return CohereMessage.builder()
                    .role(TOOL)
                    .toolCallId(toolExecutionResultMessage.id())
                    .content(List.of(new CohereMessageTextContent(toolExecutionResultMessage.text())))
                    .build();
        }

        throw new LangChain4jException("Unexpected message type: " + chatMessage.getClass());
    }

    private static CohereMessageContent toCohereContent(Content content) {
        // TODO: This is very brittle
        if (content instanceof TextContent textContent) {
            return new CohereMessageTextContent(textContent.text());
        }

        if (content instanceof ImageContent imageContent) {

            // TODO: See how to handle this better, seems very brittle as well.
            String url = imageContent.image().url() != null
                    ? imageContent.image().url().toString()
                    : "data:image/jpeg;base64," + imageContent.image().base64Data();

            CohereImageUrl imageUrl = CohereImageUrl.builder()
                    .url(url)
                    .detail(imageContent.detailLevel())
                    .build();

            return new CohereImageContent(imageUrl);
        }

        throw new LangChain4jException("Unsupported message content: " + content);
    }

    private static CohereTool toCohereTool(ToolSpecification toolSpecification) {
        Map<String, Object> parameters = toolSpecification.parameters() == null
                ? EMPTY_SCHEMA
                : toMap(toolSpecification.parameters());

        return CohereTool.builder()
                .type(FUNCTION)
                .function(CohereFunction.builder()
                        .name(toolSpecification.name())
                        .parameters(parameters)
                        .build())
                .build();
    }

    public static ChatResponse fromCohereChatResponse(CohereChatResponse cohereChatResponse, String modelName) {
        Optional<List<CohereMessageContent>> content = Optional.ofNullable(cohereChatResponse.message.getContent());

        String text = null;

        if (content.isPresent()) {
            text = content.get().stream()
                    .filter(c -> c.type == TEXT)
                    .findFirst()
                    .map(c -> c.text)
                    .orElse(null);
        }

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .modelName(modelName)
                .id(cohereChatResponse.id)
                .finishReason(mapFinishReason(cohereChatResponse.finishReason))
                .build();

        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(text)
                        .toolExecutionRequests(cohereChatResponse.message.getToolCalls()
                                .stream()
                                .map(CohereMapper::toToolExecutionRequest)
                                .toList())
                        .build())
                .metadata(metadata)
                .build();
    }

    private static FinishReason mapFinishReason(String finishReason) {
        if (finishReason.equals("COMPLETE")) return STOP;
        if (finishReason.equals("STOP_SEQUENCE")) return STOP;
        if (finishReason.equals("MAX_TOKENS")) return LENGTH;
        if (finishReason.equals("TOOL_CALL")) return TOOL_EXECUTION;

        return OTHER;
    }

    private static ToolExecutionRequest toToolExecutionRequest(CohereToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(toolCall.getFunction().getName())
                .arguments(toolCall.getFunction().getArguments())
                .build();
    }

    private static CohereResponseFormat toCohereResponseFormat(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return null;
        }

        // TODO: DISGUSTANG, clean this up xD
        return CohereResponseFormat.builder()
                .type(JSON_OBJECT)
                .jsonSchema(responseFormat.jsonSchema() != null
                    ? toMap(responseFormat.jsonSchema().rootElement(), true)
                    : null)
                .build();
    }
}
