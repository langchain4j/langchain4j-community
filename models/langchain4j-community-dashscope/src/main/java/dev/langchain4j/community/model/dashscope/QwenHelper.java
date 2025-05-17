package dev.langchain4j.community.model.dashscope;

import static com.alibaba.dashscope.aigc.conversation.ConversationParam.ResultFormat.MESSAGE;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.SearchInfo;
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
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
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
            case USER ->
                ((UserMessage) message)
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
            case USER ->
                ((UserMessage) message)
                        .contents().stream()
                                .map(QwenHelper::toMultiModalContent)
                                .collect(toList());
            case AI -> Collections.singletonList(Collections.singletonMap("text", ((AiMessage) message).text()));
            case SYSTEM ->
                Collections.singletonList(Collections.singletonMap("text", ((SystemMessage) message).text()));
            case TOOL_EXECUTION_RESULT ->
                Collections.singletonList(
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
                    return Collections.singletonMap(
                            "image", "data:%s;base64,%s".formatted(image.mimeType(), image.base64Data()));
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
                    return Collections.singletonMap(
                            "audio", "data:%s;base64,%s".formatted(audio.mimeType(), audio.base64Data()));
                } else {
                    return Collections.emptyMap();
                }
            case VIDEO:
                Video video = ((VideoContent) content).video();
                String videoContent;
                if (video.url() != null) {
                    videoContent = video.url().toString();
                    return Collections.singletonMap("video", videoContent);
                } else if (Utils.isNotNullOrBlank(video.base64Data())) {
                    return Collections.singletonMap(
                            "video", "data:%s;base64,%s".formatted(video.mimeType(), video.base64Data()));
                } else {
                    return Collections.emptyMap();
                }
            case TEXT:
                return Collections.singletonMap("text", ((TextContent) content).text());
            default:
                return Collections.emptyMap();
        }
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
                .filter(Utils::isNotNullOrEmpty)
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

    static String reasoningContentFrom(GenerationResult result) {
        return Optional.of(result)
                .map(GenerationResult::getOutput)
                .map(GenerationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(Choice::getMessage)
                .map(Message::getReasoningContent)
                .orElse(null);
    }

    static String reasoningContentFrom(MultiModalConversationResult result) {
        return Optional.of(result)
                .map(MultiModalConversationResult::getOutput)
                .map(MultiModalConversationOutput::getChoices)
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(MultiModalConversationOutput.Choice::getMessage)
                .map(MultiModalMessage::getReasoningContent)
                .orElse(null);
    }

    static boolean isMultimodalModelName(String modelName) {
        // rough judgment
        return modelName.contains("-vl-") || modelName.contains("-audio-");
    }

    static boolean isSupportingIncrementalOutputModelName(String modelName) {
        // rough judgment
        return !modelName.contains("-mt-");
    }

    static boolean isMultimodalModel(ChatRequest chatRequest) {
        if (!(chatRequest.parameters() instanceof QwenChatRequestParameters qwenParameters)) {
            throw new IllegalArgumentException("parameters should be an instance of QwenChatRequestParameters");
        }

        String modelName = qwenParameters.modelName();
        Boolean isMultimodalModel = qwenParameters.isMultimodalModel();
        isMultimodalModel = getOrDefault(isMultimodalModel, isMultimodalModelName(modelName));

        return Boolean.TRUE.equals(isMultimodalModel);
    }

    static boolean supportIncrementalOutput(ChatRequest chatRequest) {
        if (!(chatRequest.parameters() instanceof QwenChatRequestParameters qwenParameters)) {
            throw new IllegalArgumentException("parameters should be an instance of QwenChatRequestParameters");
        }

        String modelName = qwenParameters.modelName();
        Boolean supportIncrementalOutput = qwenParameters.supportIncrementalOutput();
        supportIncrementalOutput =
                getOrDefault(supportIncrementalOutput, isSupportingIncrementalOutputModelName(modelName));

        return Boolean.TRUE.equals(supportIncrementalOutput);
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

    private static JsonObject toParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            return JsonUtils.toJsonObject(toMap(toolSpecification.parameters()));
        } else {
            return JsonUtils.toJsonObject(Map.of());
        }
    }

    static ChatResponse chatResponseFrom(String modelName, GenerationResult result) {
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(result))
                .metadata(QwenChatResponseMetadata.builder()
                        .id(result.getRequestId())
                        .modelName(modelName)
                        .tokenUsage(tokenUsageFrom(result))
                        .finishReason(finishReasonFrom(result))
                        .searchInfo(convertSearchInfo(result.getOutput().getSearchInfo()))
                        .reasoningContent(reasoningContentFrom(result))
                        .build())
                .build();
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

    static ChatResponse chatResponseFrom(String modelName, MultiModalConversationResult result) {
        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(result))
                .metadata(QwenChatResponseMetadata.builder()
                        .id(result.getRequestId())
                        .modelName(modelName)
                        .tokenUsage(tokenUsageFrom(result))
                        .finishReason(finishReasonFrom(result))
                        .reasoningContent(reasoningContentFrom(result))
                        .build())
                .build();
    }

    static AiMessage aiMessageFrom(MultiModalConversationResult result) {
        return new AiMessage(answerFrom(result));
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
                if (acc.getFirst().type() == SYSTEM) {
                    log.warn("Drop existed system message: {}", acc);
                    acc.removeFirst();
                }
                acc.addFirst(message);
                return acc;
            }

            ChatMessageType lastType = acc.getLast().type();
            if (lastType == SYSTEM && type != USER) {
                log.warn("The first non-system message must be a user message, but found: {}", message);
                return acc;
            }

            if (type == USER) {
                while (!acc.isEmpty() && acc.getLast().type() != SYSTEM && !isNormalAiType(acc.getLast())) {
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

    public static Response<AiMessage> convertResponse(ChatResponse chatResponse) {
        return Response.from(
                chatResponse.aiMessage(),
                chatResponse.metadata().tokenUsage(),
                chatResponse.metadata().finishReason(),
                ((QwenChatResponseMetadata) chatResponse.metadata()).toMap());
    }

    static StreamingChatResponseHandler convertHandler(StreamingResponseHandler<AiMessage> handler) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onComplete(convertResponse(completeResponse));
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };
    }

    static QwenChatResponseMetadata.SearchInfo convertSearchInfo(
            com.alibaba.dashscope.aigc.generation.SearchInfo searchInfo) {
        List<QwenChatResponseMetadata.SearchResult> searchResults =
                isNull(searchInfo) || isNullOrEmpty(searchInfo.getSearchResults())
                        ? Collections.emptyList()
                        : searchInfo.getSearchResults().stream()
                                .map(QwenHelper::convertSearchResult)
                                .collect(toList());

        return QwenChatResponseMetadata.SearchInfo.builder()
                .searchResults(searchResults)
                .build();
    }

    static QwenChatResponseMetadata.SearchResult convertSearchResult(SearchInfo.SearchResult searchResult) {
        return QwenChatResponseMetadata.SearchResult.builder()
                .siteName(searchResult.getSiteName())
                .icon(searchResult.getIcon())
                .index(searchResult.getIndex())
                .title(searchResult.getTitle())
                .url(searchResult.getUrl())
                .build();
    }

    static void validateGenerationParameters(QwenChatRequestParameters parameters) {
        if (parameters.vlHighResolutionImages() != null) {
            throw new UnsupportedFeatureException(
                    "'vlHighResolutionImages' parameter is not supported by " + parameters.modelName());
        }

        if (parameters.responseFormat() != null && parameters.responseFormat().jsonSchema() != null) {
            throw new UnsupportedFeatureException("JSON response format is not supported by " + parameters.modelName());
        }
    }

    static void validateMultimodalConversationParameters(QwenChatRequestParameters parameters) {
        if (parameters.searchOptions() != null) {
            throw new UnsupportedFeatureException(
                    "'searchOptions' parameter is not supported by " + parameters.modelName());
        }

        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'frequencyPenalty' parameter is not supported by " + parameters.modelName());
        }

        if (parameters.toolChoice() != null) {
            throw new UnsupportedFeatureException(
                    "'toolChoice' parameter is not supported by " + parameters.modelName());
        }

        if (!isNullOrEmpty(parameters.toolSpecifications())) {
            throw new UnsupportedFeatureException(
                    "'toolSpecifications' parameter is not supported by " + parameters.modelName());
        }

        if (parameters.translationOptions() != null) {
            throw new UnsupportedFeatureException(
                    "'translationOptions' parameter is not supported by " + parameters.modelName());
        }

        if (parameters.responseFormat() != null) {
            throw new UnsupportedFeatureException(
                    "'responseFormat' parameter is not supported by " + parameters.modelName());
        }
    }

    static GenerationParam toGenerationParam(
            String apiKey,
            ChatRequest chatRequest,
            Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer,
            boolean incrementalOutput) {
        QwenChatRequestParameters parameters = (QwenChatRequestParameters) chatRequest.parameters();
        validateGenerationParameters(parameters);

        GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                .apiKey(apiKey)
                .model(parameters.modelName())
                .topP(parameters.topP())
                .topK(parameters.topK())
                .enableSearch(getOrDefault(parameters.enableSearch(), false))
                .searchOptions(toQwenSearchOptions(parameters.searchOptions()))
                .seed(parameters.seed())
                .repetitionPenalty(frequencyPenaltyToRepetitionPenalty(parameters.frequencyPenalty()))
                .maxTokens(parameters.maxOutputTokens())
                .messages(toQwenMessages(chatRequest.messages()))
                .responseFormat(toQwenResponseFormat(parameters.responseFormat()))
                .resultFormat(MESSAGE)
                .incrementalOutput(incrementalOutput)
                .enableThinking(parameters.enableThinking())
                .thinkingBudget(parameters.thinkingBudget());

        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature().floatValue());
        }

        if (parameters.stopSequences() != null) {
            builder.stopStrings(parameters.stopSequences());
        }

        if (!isNullOrEmpty(parameters.toolSpecifications())) {
            builder.tools(toToolFunctions(parameters.toolSpecifications()));
            if (parameters.toolChoice() != null && parameters.toolChoice() == REQUIRED) {
                builder.toolChoice(
                        toToolFunction((parameters.toolSpecifications().get(0))));
            }
        }

        if (parameters.translationOptions() != null) {
            // no java field is provided yet
            builder.parameter("translation_options", toQwenTranslationOptions(parameters.translationOptions()));
        }

        if (parameters.custom() != null) {
            // no java field is provided yet
            builder.parameter("custom", parameters.custom());
        }

        if (generationParamCustomizer != null) {
            generationParamCustomizer.accept(builder);
        }

        return builder.build();
    }

    static MultiModalConversationParam toMultiModalConversationParam(
            String apiKey,
            ChatRequest chatRequest,
            Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>>
                    multimodalConversationParamCustomizer,
            boolean incrementalOutput) {
        QwenChatRequestParameters parameters = (QwenChatRequestParameters) chatRequest.parameters();
        validateMultimodalConversationParameters(parameters);

        MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                MultiModalConversationParam.builder()
                        .apiKey(apiKey)
                        .model(parameters.modelName())
                        .topP(parameters.topP())
                        .topK(parameters.topK())
                        .enableSearch(getOrDefault(parameters.enableSearch(), false))
                        .seed(parameters.seed())
                        .maxTokens(parameters.maxOutputTokens())
                        .messages(toQwenMultiModalMessages(chatRequest.messages()))
                        .incrementalOutput(incrementalOutput);

        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature().floatValue());
        }

        if (!isNullOrEmpty(parameters.stopSequences())) {
            builder.parameter("stop", parameters.stopSequences());
        }

        if (parameters.vlHighResolutionImages() != null) {
            // no java field is provided yet
            builder.parameter("vl_high_resolution_images", parameters.vlHighResolutionImages());
        }

        if (parameters.custom() != null) {
            // no java field is provided yet
            builder.parameter("custom", parameters.custom());
        }

        if (multimodalConversationParamCustomizer != null) {
            multimodalConversationParamCustomizer.accept(builder);
        }

        return builder.build();
    }

    static com.alibaba.dashscope.common.ResponseFormat toQwenResponseFormat(ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }

        return switch (responseFormat.type()) {
            case JSON ->
                com.alibaba.dashscope.common.ResponseFormat.from(
                        com.alibaba.dashscope.common.ResponseFormat.JSON_OBJECT);
            case TEXT ->
                com.alibaba.dashscope.common.ResponseFormat.from(com.alibaba.dashscope.common.ResponseFormat.TEXT);
        };
    }

    static com.alibaba.dashscope.aigc.generation.SearchOptions toQwenSearchOptions(
            QwenChatRequestParameters.SearchOptions searchOptions) {
        if (searchOptions == null) {
            return null;
        }

        return com.alibaba.dashscope.aigc.generation.SearchOptions.builder()
                .citationFormat(searchOptions.citationFormat())
                .enableCitation(searchOptions.enableCitation())
                .enableSource(searchOptions.enableSource())
                .forcedSearch(searchOptions.forcedSearch())
                .searchStrategy(searchOptions.searchStrategy())
                .build();
    }

    static Map<String, Object> toQwenTranslationOptions(
            QwenChatRequestParameters.TranslationOptions translationOptions) {
        if (translationOptions == null) {
            return null;
        }

        // no java class is provided yet
        Map<String, Object> translationOptionsMap = new HashMap<>(5);
        translationOptionsMap.put("source_lang", translationOptions.sourceLang());
        translationOptionsMap.put("target_lang", translationOptions.targetLang());
        translationOptionsMap.put("terms", toTermList(translationOptions.terms()));
        translationOptionsMap.put("tm_list", toTermList(translationOptions.tmList()));
        translationOptionsMap.put("domains", translationOptions.domains());
        return translationOptionsMap;
    }

    static List<Map<String, String>> toTermList(List<QwenChatRequestParameters.TranslationOptionTerm> list) {
        if (list == null) {
            return null;
        }
        return list.stream()
                .map(term -> Map.of("source", term.source(), "target", term.target()))
                .collect(toList());
    }

    static Float frequencyPenaltyToRepetitionPenalty(Double frequencyPenalty) {
        // repetitionPenalty: https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api#2ed5ee7377fum
        // frequencyPenalty: https://platform.openai.com/docs/api-reference/chat/create#chat-create-frequency_penalty
        // map: [-2, 2] -> (0, ∞), and 0 -> 1
        // use logit function (https://en.wikipedia.org/wiki/Logit)

        if (frequencyPenalty == null) {
            return null;
        } else if (frequencyPenalty >= 2) {
            return Float.POSITIVE_INFINITY;
        } else if (frequencyPenalty < -2) {
            throw new IllegalArgumentException("Value of frequencyPenalty must be within [-2.0, 2.0]");
        }

        // limit the input to 0.5 to 1 (as the repetition penalty is a positive value)
        double x = (frequencyPenalty + 6) / 8;
        // make sure repetition penalty is 1 when frequency penalty is 0
        double denominator = logit(0.75d);

        return (float) (logit(x) / denominator);
    }

    static Double repetitionPenaltyToFrequencyPenalty(Float repetitionPenalty) {
        // repetitionPenalty: https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api#2ed5ee7377fum
        // frequencyPenalty: https://platform.openai.com/docs/api-reference/chat/create#chat-create-frequency_penalty
        // map: (0, ∞) -> [-2, 2], and 1 -> 0
        // use sigmoid function (https://en.wikipedia.org/wiki/Sigmoid_function)

        if (repetitionPenalty == null) {
            return null;
        } else if (repetitionPenalty <= 0) {
            throw new IllegalArgumentException("Value of repetitionPenalty must be positive number");
        }

        // make sure frequency penalty is 0 when repetition penalty is 1
        // see frequencyPenaltyToRepetitionPenalty()
        double factor = logit(0.75d);
        double y = sigmoid(repetitionPenalty.doubleValue() * factor);

        // make sure frequency penalty is between -2 and 2
        return y * 8 - 6;
    }

    private static double logit(double x) {
        return Math.log(x / (1 - x));
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
