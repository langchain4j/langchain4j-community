package dev.langchain4j.community.model.minimax;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.minimax.client.chat.Function;
import dev.langchain4j.community.model.minimax.client.chat.Parameters;
import dev.langchain4j.community.model.minimax.client.chat.Tool;
import dev.langchain4j.community.model.minimax.client.chat.ToolType;
import dev.langchain4j.community.model.minimax.client.chat.message.AssistantMessage;
import dev.langchain4j.community.model.minimax.client.chat.message.FunctionCall;
import dev.langchain4j.community.model.minimax.client.chat.message.Message;
import dev.langchain4j.community.model.minimax.client.chat.message.SystemMessage;
import dev.langchain4j.community.model.minimax.client.chat.message.ToolCall;
import dev.langchain4j.community.model.minimax.client.chat.message.ToolMessage;
import dev.langchain4j.community.model.minimax.client.chat.message.UserMessage;
import dev.langchain4j.community.model.minimax.client.shared.CompletionUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;

class InternalMiniMaxHelper {

    static List<Message> toMiniMaxMessages(List<ChatMessage> messages) {
        if (isNullOrEmpty(messages)) {
            return null;
        }
        return messages.stream()
                .map(msg -> {
                    if (msg instanceof dev.langchain4j.data.message.SystemMessage message) {
                        return SystemMessage.of(message.text());
                    } else if (msg instanceof AiMessage message) {
                        if (!message.hasToolExecutionRequests()) {
                            return AssistantMessage.of(message.text());
                        }
                        List<ToolCall> list = message.toolExecutionRequests().stream()
                                .map(it -> ToolCall.builder()
                                        .id(it.id())
                                        .type(ToolType.FUNCTION)
                                        .function(FunctionCall.builder()
                                                .id(it.id())
                                                .name(it.name())
                                                .arguments(it.arguments())
                                                .build())
                                        .build())
                                .toList();
                        return AssistantMessage.of(message.text(), list.toArray(new ToolCall[0]));
                    } else if (msg instanceof dev.langchain4j.data.message.UserMessage message) {
                        if (message.hasSingleText()) {
                            return UserMessage.builder()
                                    .content(message.singleText())
                                    .name(message.name())
                                    .build();
                        } else {
                            // MiniMax supports text content in multimodal messages
                            StringBuilder textContent = new StringBuilder();
                            message.contents().forEach(item -> {
                                if (item instanceof TextContent content) {
                                    textContent.append(content.text());
                                }
                            });
                            return UserMessage.builder()
                                    .content(textContent.toString())
                                    .name(message.name())
                                    .build();
                        }
                    } else if (msg instanceof ToolExecutionResultMessage message) {
                        return ToolMessage.of(message.id(), message.text());
                    }
                    throw illegalArgument("Unknown message type: " + msg.type());
                })
                .toList();
    }

    static List<Tool> toTools(List<ToolSpecification> toolSpecifications) {
        if (isNullOrEmpty(toolSpecifications)) {
            return null;
        }
        return toolSpecifications.stream().map(InternalMiniMaxHelper::toTool).toList();
    }

    static Tool toTool(ToolSpecification toolSpecification) {
        return Tool.builder()
                .function(Function.builder()
                        .description(toolSpecification.description())
                        .name(toolSpecification.name())
                        .parameters(toParameters(toolSpecification))
                        .build())
                .build();
    }

    static AiMessage aiMessageFrom(AssistantMessage assistantMessage) {
        String text = assistantMessage.getContent();
        List<ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream()
                    .filter(toolCall -> toolCall.getType() == ToolType.FUNCTION)
                    .map(InternalMiniMaxHelper::toToolExecutionRequest)
                    .toList();
            return isNullOrBlank(text)
                    ? AiMessage.from(toolExecutionRequests)
                    : AiMessage.from(text, toolExecutionRequests);
        }
        return AiMessage.from(text);
    }

    private static ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        FunctionCall functionCall = toolCall.getFunction();
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(functionCall.getName())
                .arguments(functionCall.getArguments())
                .build();
    }

    private static Parameters toParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            return Parameters.builder()
                    .properties(toMap(parameters.properties()))
                    .required(parameters.required())
                    .build();
        } else {
            return null;
        }
    }

    public static TokenUsage tokenUsageFrom(CompletionUsage usage) {
        if (Objects.isNull(usage)) {
            return null;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    public static FinishReason finishReasonFrom(String finishReason) {
        if (isNullOrBlank(finishReason)) {
            return null;
        }
        return switch (finishReason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            case "tool_calls", "function_call" -> FinishReason.TOOL_EXECUTION;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            default -> null;
        };
    }
}
