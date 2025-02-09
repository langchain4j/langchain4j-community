package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.toMap;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.alibaba.dashscope.utils.JsonUtils;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class QwenHelper {

    private static final Logger log = LoggerFactory.getLogger(QwenHelper.class);

    static List<Message> toQwenMessages(List<ChatMessage> messages) {
        return sanitizeMessages(messages).stream()
                .map(QwenHelper::toQwenMessage)
                .collect(toList());
    }

    static List<Message> toQwenMessages(Iterable<ChatMessage> messages) {
        LinkedList<Message> qwenMessages = new LinkedList<>();
        messages.forEach(message -> qwenMessages.add(toQwenMessage(message)));
        return qwenMessages;
    }

    static Message toQwenMessage(ChatMessage message) {
        return Message.builder()
                .role(roleFrom(message))
                .content(toSingleText(message))
                .name(nameFrom(message))
                .toolCallId(toolCallIdFrom(message))
                .toolCalls(toolCallsFrom(message))
                .build();
    }

    static String toSingleText(ChatMessage message) {
        return switch (message.type()) {
            case USER -> ((UserMessage) message)
                    .contents().stream()
                            .filter(TextContent.class::isInstance)
                            .map(TextContent.class::cast)
                            .map(TextContent::text)
                            .collect(joining("\n"));
            case AI -> ((AiMessage) message).text();
            case SYSTEM -> ((SystemMessage) message).text();
            case TOOL_EXECUTION_RESULT -> ((ToolExecutionResultMessage) message).text();
            default -> "";
        };
    }

    static String nameFrom(ChatMessage message) {
        return switch (message.type()) {
            case USER -> ((UserMessage) message).name();
            case TOOL_EXECUTION_RESULT -> ((ToolExecutionResultMessage) message).toolName();
            default -> null;
        };
    }

    static String toolCallIdFrom(ChatMessage message) {
        if (message.type() == TOOL_EXECUTION_RESULT) {
            return ((ToolExecutionResultMessage) message).id();
        }
        return null;
    }

    static List<ToolCallBase> toolCallsFrom(ChatMessage message) {
        if (message.type() == AI && ((AiMessage) message).hasToolExecutionRequests()) {
            return toToolCalls(((AiMessage) message).toolExecutionRequests());
        }
        return null;
    }

    static List<MultiModalMessage> toQwenMultiModalMessages(List<ChatMessage> messages) {
        return messages.stream().map(QwenHelper::toQwenMultiModalMessage).collect(toList());
    }

    static MultiModalMessage toQwenMultiModalMessage(ChatMessage message) {
        return MultiModalMessage.builder()
                .role(roleFrom(message))
                .content(toMultiModalContents(message))
                .build();
    }

    static List<Map<String, Object>> toMultiModalContents(ChatMessage message) {
        return switch (message.type()) {
            case USER -> ((UserMessage) message)
                    .contents().stream().map(QwenHelper::toMultiModalContent).collect(toList());
            case AI -> Collections.singletonList(Collections.singletonMap("text", ((AiMessage) message).text()));
            case SYSTEM -> Collections.singletonList(
                    Collections.singletonMap("text", ((SystemMessage) message).text()));
            case TOOL_EXECUTION_RESULT -> Collections.singletonList(
                    Collections.singletonMap("text", ((ToolExecutionResultMessage) message).text()));
            default -> Collections.emptyList();
        };
    }

    static Map<String, Object> toMultiModalContent(Content content) {
        switch (content.type()) {
            case IMAGE:
                Image image = ((ImageContent) content).image();
                String imageContent;
                if (image.url() != null) {
                    imageContent = image.url().toString();
                    return Collections.singletonMap("image", imageContent);
                } else if (Utils.isNotNullOrBlank(image.base64Data())) {
                    // The dashscope sdk supports local file url: file://...
                    // Using the temporary directory for storing temporary files is a safe practice,
                    // as most operating systems will periodically clean up the contents of this directory
                    // or do so upon system reboot.
                    imageContent = saveDataAsTemporaryFile(image.base64Data(), image.mimeType());

                    // In this case, the dashscope sdk requires a mutable map.
                    HashMap<String, Object> contentMap = new HashMap<>(1);
                    contentMap.put("image", imageContent);
                    return contentMap;
                } else {
                    return Collections.emptyMap();
                }
            case AUDIO:
                Audio audio = ((AudioContent) content).audio();
                String audioContent;
                if (audio.url() != null) {
                    audioContent = audio.url().toString();
                    return Collections.singletonMap("audio", audioContent);
                } else if (Utils.isNotNullOrBlank(audio.base64Data())) {
                    // The dashscope sdk supports local file url: file://...
                    // Using the temporary directory for storing temporary files is a safe practice,
                    // as most operating systems will periodically clean up the contents of this directory
                    // or do so upon system reboot.
                    audioContent = saveDataAsTemporaryFile(audio.base64Data(), audio.mimeType());

                    // In this case, the dashscope sdk requires a mutable map.
                    HashMap<String, Object> contentMap = new HashMap<>(1);
                    contentMap.put("audio", audioContent);
                    return contentMap;
                } else {
                    return Collections.emptyMap();
                }
            case TEXT:
                return Collections.singletonMap("text", ((TextContent) content).text());
            default:
                return Collections.emptyMap();
        }
    }

    static String saveDataAsTemporaryFile(String base64Data, String mimeType) {
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        String tmpFileName = UUID.randomUUID().toString();
        if (Utils.isNotNullOrBlank(mimeType)) {
            // e.g. "image/png", "image/jpeg"...
            int lastSlashIndex = mimeType.lastIndexOf("/");
            if (lastSlashIndex >= 0 && lastSlashIndex < mimeType.length() - 1) {
                String fileSuffix = mimeType.substring(lastSlashIndex + 1);
                tmpFileName = tmpFileName + "." + fileSuffix;
            }
        }

        Path tmpFilePath = Paths.get(tmpDir, tmpFileName);
        byte[] data = Base64.getDecoder().decode(base64Data);
        try {
            Files.copy(new ByteArrayInputStream(data), tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return tmpFilePath.toAbsolutePath().toUri().toString();
    }

    static String roleFrom(ChatMessage message) {
        if (message.type() == AI) {
            return Role.ASSISTANT.getValue();
        } else if (message.type() == SYSTEM) {
            return Role.SYSTEM.getValue();
        } else if (message.type() == TOOL_EXECUTION_RESULT) {
            return Role.TOOL.getValue();
        } else {
            return Role.USER.getValue();
        }
    }

    static boolean hasAnswer(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getContent)
                .filter(Utils::isNotNullOrBlank)
                .isPresent();
    }

    static String answerFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getContent)
                // Compatible with some older models.
                .orElseGet(() -> Optional.of(result)
                        .map(GenerationResult::getOutput)
                        .map(GenerationOutput::getText)
                        // Model may send empty content in streaming mode
                        .orElse(""));
    }

    static boolean hasAnswer(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getContent)
                .filter(contents -> !contents.isEmpty())
                .isPresent();
    }

    static String answerFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getContent)
                .filter(contents -> !contents.isEmpty())
                .map(contents -> contents.get(0))
                .map(content -> content.get("text"))
                .map(String.class::cast)
                // Model may send empty content in streaming mode
                .orElse("");
    }

    static TokenUsage tokenUsageFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getUsage)
                .map(usage -> new TokenUsage(usage.getInputTokens(), usage.getOutputTokens()))
                .orElse(null);
    }

    static FinishReason finishReasonFrom(GenerationResult result) {
        Choice choice = result.getOutput().getChoices().get(0);
        // Upon observation, when tool_calls occur, the returned finish_reason may be null or "stop", not "tool_calls".
        String finishReason =
                isNullOrEmpty(choice.getMessage().getToolCalls()) ? choice.getFinishReason() : "tool_calls";

        return finishReason == null
                ? null
                : switch (finishReason) {
                    case "stop" -> STOP;
                    case "length" -> LENGTH;
                    case "tool_calls" -> TOOL_EXECUTION;
                    default -> null;
                };
    }

    static FinishReason finishReasonFrom(MultiModalConversationResult result) {
        String finishReason = Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getFinishReason)
                .orElse("");

        return switch (finishReason) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            default -> null;
        };
    }

    public static boolean isMultimodalModel(String modelName) {
        return modelName.contains("-vl-") || modelName.contains("-audio-");
    }

    static List<ToolBase> toToolFunctions(Collection<ToolSpecification> toolSpecifications) {
        if (isNullOrEmpty(toolSpecifications)) {
            return Collections.emptyList();
        }

        return toolSpecifications.stream().map(QwenHelper::toToolFunction).collect(toList());
    }

    static ToolBase toToolFunction(ToolSpecification toolSpecification) {
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name(toolSpecification.name())
                .description(getOrDefault(toolSpecification.description(), ""))
                .parameters(toParameters(toolSpecification))
                .build();
        return ToolFunction.builder().function(functionDefinition).build();
    }

    @SuppressWarnings("deprecation")
    private static JsonObject toParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            return JsonUtils.toJsonObject(toMap(toolSpecification.parameters()));
        } else if (toolSpecification.toolParameters() != null) {
            return JsonUtils.toJsonObject(toolSpecification.toolParameters());
        } else {
            return JsonUtils.toJsonObject(Collections.emptyMap());
        }
    }

    static AiMessage aiMessageFrom(GenerationResult result) {
        if (isFunctionToolCalls(result)) {
            String text = answerFrom(result);
            return isNullOrBlank(text)
                    ? new AiMessage(toolExecutionRequestsFrom(result))
                    : new AiMessage(text, toolExecutionRequestsFrom(result));
        } else {
            return new AiMessage(answerFrom(result));
        }
    }

    private static List<ToolExecutionRequest> toolExecutionRequestsFrom(GenerationResult result) {
        return toolCallsFrom(result).stream()
                .filter(ToolCallFunction.class::isInstance)
                .map(ToolCallFunction.class::cast)
                .map(toolCall -> ToolExecutionRequest.builder()
                        .id(getOrDefault(toolCall.getId(), () -> toolCallIdFromMessage(result)))
                        .name(toolCall.getFunction().getName())
                        .arguments(toolCall.getFunction().getArguments())
                        .build())
                .collect(toList());
    }

    static List<ToolCallBase> toolCallsFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getToolCalls)
                .orElseThrow(IllegalStateException::new);
    }

    static String toolCallIdFromMessage(GenerationResult result) {
        // Not sure about the difference between Message::getToolCallId() and ToolCallFunction::getId().
        // Encapsulate a method to get the ID using Message::getToolCallId() when ToolCallFunction::getId() is null.
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getToolCallId)
                .orElse(null);
    }

    static boolean isFunctionToolCalls(GenerationResult result) {
        Optional<List<ToolCallBase>> toolCallBases = Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getToolCalls);
        return toolCallBases.isPresent() && !isNullOrEmpty(toolCallBases.get());
    }

    private static List<ToolCallBase> toToolCalls(Collection<ToolExecutionRequest> toolExecutionRequests) {
        return toolExecutionRequests.stream().map(QwenHelper::toToolCall).collect(toList());
    }

    private static ToolCallBase toToolCall(ToolExecutionRequest toolExecutionRequest) {
        ToolCallFunction toolCallFunction = new ToolCallFunction();
        toolCallFunction.setId(toolExecutionRequest.id());
        ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
        callFunction.setName(toolExecutionRequest.name());
        callFunction.setArguments(toolExecutionRequest.arguments());
        toolCallFunction.setFunction(callFunction);
        return toolCallFunction;
    }

    static List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        LinkedList<ChatMessage> sanitizedMessages =
                messages.stream().reduce(new LinkedList<>(), messageAccumulator(), messageCombiner());

        // Ensure the last message is a user/tool_execution_result message
        while (!sanitizedMessages.isEmpty() && !isInputMessageType(sanitizedMessages.getLast())) {
            ChatMessage removedMessage = sanitizedMessages.removeLast();
            log.warn("The last message should be a user/tool_execution_result message, but found: {}", removedMessage);
        }

        return sanitizedMessages;
    }

    private static BiFunction<LinkedList<ChatMessage>, ChatMessage, LinkedList<ChatMessage>> messageAccumulator() {
        return (acc, message) -> {
            ChatMessageType type = message.type();
            if (acc.isEmpty()) {
                // Ensure the first message is a system message or a user message.
                if (type == SYSTEM || type == USER) {
                    acc.add(message);
                } else {
                    log.warn("The first message should be a system message or a user message, but found: {}", message);
                }
                return acc;
            }

            if (type == SYSTEM) {
                log.warn("The system message should be the first message. Drop existed messages: {}", acc);
                acc.clear();
                acc.add(message);
                return acc;
            }

            ChatMessageType lastType = acc.getLast().type();
            if (lastType == SYSTEM && type != USER) {
                log.warn("The first non-system message must be a user message, but found: {}", message);
                return acc;
            }

            if (type == USER) {
                while (acc.getLast().type() != SYSTEM && !isNormalAiType(acc.getLast())) {
                    ChatMessage removedMessage = acc.removeLast();
                    log.warn(
                            "Tool execution result should follow a tool execution request message. Drop duplicated message: {}",
                            removedMessage);
                }
            } else if (type == TOOL_EXECUTION_RESULT) {
                while (!isToolExecutionRequestsAiType(acc.getLast())) {
                    ChatMessage removedMessage = acc.removeLast();
                    log.warn(
                            "Tool execution result should follow a tool execution request message. Drop duplicated message: {}",
                            removedMessage);
                }
            } else if (type == AI) {
                while (!isInputMessageType(acc.getLast())) {
                    ChatMessage removedMessage = acc.removeLast();
                    log.warn(
                            "AI message should follow a user/tool_execution_result message. Drop duplicated message: {}",
                            removedMessage);
                }
            }

            acc.add(message);
            return acc;
        };
    }

    private static BinaryOperator<LinkedList<ChatMessage>> messageCombiner() {
        return (acc1, acc2) -> {
            throw new UnsupportedOperationException("Parallel stream not supported");
        };
    }

    private static boolean isInputMessageType(ChatMessage message) {
        ChatMessageType type = message.type();
        return type == USER || type == TOOL_EXECUTION_RESULT;
    }

    private static boolean isNormalAiType(ChatMessage message) {
        return message.type() == AI && !((AiMessage) message).hasToolExecutionRequests();
    }

    private static boolean isToolExecutionRequestsAiType(ChatMessage message) {
        return message.type() == AI && ((AiMessage) message).hasToolExecutionRequests();
    }

    static ChatModelRequest createModelListenerRequest(
            GenerationParam request, List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        Double temperature =
                request.getTemperature() != null ? request.getTemperature().doubleValue() : null;
        return ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(temperature)
                .topP(request.getTopP())
                .maxTokens(request.getMaxTokens())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelRequest createModelListenerRequest(
            MultiModalConversationParam request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        Double temperature =
                request.getTemperature() != null ? request.getTemperature().doubleValue() : null;
        return ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(temperature)
                .topP(request.getTopP())
                .maxTokens(request.getMaxLength())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelResponse createModelListenerResponse(
            String responseId, String responseModel, Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }

    static void onListenRequest(
            List<ChatModelListener> listeners, ChatModelRequest modelListenerRequest, Map<Object, Object> attributes) {
        ChatModelRequestContext context = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    static void onListenResponse(
            List<ChatModelListener> listeners,
            String responseId,
            Response<AiMessage> response,
            ChatModelRequest modelListenerRequest,
            Map<Object, Object> attributes) {
        ChatModelResponse modelListenerResponse =
                createModelListenerResponse(responseId, modelListenerRequest.model(), response);

        ChatModelResponseContext context =
                new ChatModelResponseContext(modelListenerResponse, modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onResponse(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    static void onListenError(
            List<ChatModelListener> listeners,
            String responseId,
            Throwable error,
            ChatModelRequest modelListenerRequest,
            Response<AiMessage> partialResponse,
            Map<Object, Object> attributes) {
        ChatModelResponse partialModelListenerResponse =
                createModelListenerResponse(responseId, modelListenerRequest.model(), partialResponse);
        ChatModelErrorContext context =
                new ChatModelErrorContext(error, modelListenerRequest, partialModelListenerResponse, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onError(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }
}
