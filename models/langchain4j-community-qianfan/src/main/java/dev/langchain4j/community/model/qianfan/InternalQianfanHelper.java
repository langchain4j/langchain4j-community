package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.qianfan.client.chat.Function;
import dev.langchain4j.community.model.qianfan.client.chat.FunctionCall;
import dev.langchain4j.community.model.qianfan.client.chat.Message;
import dev.langchain4j.community.model.qianfan.client.chat.Parameters;
import dev.langchain4j.community.model.qianfan.client.chat.Role;
import dev.langchain4j.community.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.community.model.qianfan.client.embedding.EmbeddingResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Internal
class InternalQianfanHelper {

    private InternalQianfanHelper() throws InstantiationException {
        throw new InstantiationException("Can not instantiate utility class");
    }

    static List<Function> toFunctions(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalQianfanHelper::toFunction)
                .collect(toList());
    }

    static Message toQianfanMessage(ChatMessage message) {

        if (message instanceof final UserMessage userMessage) {
            return Message.builder()
                    .role(Role.USER)
                    .content(userMessage.singleText())
                    .name(userMessage.name())
                    .build();
        }

        if (message instanceof final AiMessage aiMessage) {

            if (!aiMessage.hasToolExecutionRequests()) {

                return Message.builder()
                        .content(aiMessage.text())
                        .role(Role.ASSISTANT)
                        .build();
            }

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            if (toolExecutionRequest.id() == null) {
                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build();

                return Message.builder()
                        .content(aiMessage.text())
                        .role(Role.ASSISTANT)
                        .functionCall(functionCall)
                        .build();
            }
        }
        if (message instanceof final ToolExecutionResultMessage toolExecutionResultMessage) {

            FunctionCall functionCall = FunctionCall.builder()
                    .name(toolExecutionResultMessage.toolName())
                    .arguments(toolExecutionResultMessage.text())
                    .build();
            return Message.builder()
                    .content(toolExecutionResultMessage.text())
                    .role(Role.FUNCTION)
                    .name(functionCall.getName())
                    .build();
        }
        throw illegalArgument("Unknown message type: " + message.type());
    }

    static TokenUsage tokenUsageFrom(ChatCompletionResponse response) {
        return Optional.of(response)
                .map(ChatCompletionResponse::getUsage)
                .map(usage ->
                        new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()))
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(CompletionResponse response) {
        return Optional.of(response)
                .map(CompletionResponse::getUsage)
                .map(usage ->
                        new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()))
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(EmbeddingResponse response) {
        return Optional.of(response)
                .map(EmbeddingResponse::getUsage)
                .map(usage ->
                        new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()))
                .orElse(null);
    }

    static FinishReason finishReasonFrom(String finishReason) {

        if (Utils.isNullOrBlank(finishReason)) {
            return null;
        }

        return switch (finishReason) {
            case "normal", "stop" -> STOP;
            case "length" -> LENGTH;
            case "content_filter" -> CONTENT_FILTER;
            case "function_call" -> TOOL_EXECUTION;
            default -> null;
        };
    }

    static AiMessage aiMessageFrom(ChatCompletionResponse response) {

        FunctionCall functionCall = response.getFunctionCall();

        if (functionCall != null) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.getName())
                    .arguments(functionCall.getArguments())
                    .build();
            return aiMessage(toolExecutionRequest);
        }

        return aiMessage(response.getResult());
    }

    static String getSystemMessage(List<ChatMessage> messages) {

        List<ChatMessage> systemMessages = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .collect(toList());

        if (systemMessages.size() > 1) {
            throw new RuntimeException("Multiple system messages are not supported");
        }

        if (Utils.isNullOrEmpty(systemMessages)) {
            return null;
        }

        return ((SystemMessage) systemMessages.get(0)).text();
    }

    static List<Message> toOpenAiMessages(List<ChatMessage> messages) {
        List<Message> aiMessages = messages.stream()
                .filter(chatMessage -> !(chatMessage instanceof SystemMessage))
                .map(InternalQianfanHelper::toQianfanMessage)
                .collect(toList());

        // ensure the length of messages is odd
        if (aiMessages.isEmpty() || aiMessages.size() % 2 == 1) {
            return aiMessages;
        }

        aiMessages.remove(0);
        return aiMessages;
    }

    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(getOrDefault(toolSpecification.description(), toolSpecification.name()))
                .parameters(toOpenAiParameters(toolSpecification))
                .build();
    }

    private static Parameters toOpenAiParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            return Parameters.builder()
                    .properties(toMap(parameters.properties()))
                    .required(parameters.required())
                    .build();
        } else {
            return Parameters.builder().build();
        }
    }
}
